package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.StreamHelper;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.repository.VcsFile;
import svnserver.repository.git.prop.GitProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFile implements VcsFile {
  @NotNull
  private final GitRepository repo;
  @NotNull
  private final GitProperty[] props;
  @NotNull
  private final GitTreeEntry treeEntry;
  @NotNull
  private final String parentPath;

  private final int revision;

  // Cache
  @Nullable
  private ObjectLoader objectLoader;
  @Nullable
  private Iterable<GitTreeEntry> rawEntriesCache;
  @Nullable
  private Iterable<GitFile> treeEntriesCache;
  @Nullable
  private String fullPathCache;

  public GitFile(@NotNull GitRepository repo, @NotNull GitTreeEntry treeEntry, @NotNull String parentPath, @NotNull GitProperty[] parentProps, int revision) throws IOException, SVNException {
    this.repo = repo;
    this.treeEntry = treeEntry;
    this.parentPath = parentPath;
    this.props = GitProperty.joinProperties(parentProps, treeEntry.getFileName(), treeEntry.getFileMode(), repo.collectProperties(treeEntry, this::getRawEntries));
    this.revision = revision;
  }

  public GitFile(@NotNull GitRepository repo, @NotNull RevCommit commit, int revisionId) throws IOException, SVNException {
    this(repo, new GitTreeEntry(repo.getRepository(), FileMode.TREE, commit.getTree(), ""), "", GitProperty.emptyArray, revisionId);
  }

  @NotNull
  @Override
  public String getFileName() {
    return treeEntry.getFileName();
  }

  @NotNull
  public String getFullPath() {
    if (fullPathCache == null) {
      fullPathCache = StringHelper.joinPath(parentPath, getFileName());
    }
    return fullPathCache;
  }

  @NotNull
  public GitTreeEntry getTreeEntry() {
    return treeEntry;
  }

  @NotNull
  public FileMode getFileMode() {
    return treeEntry.getFileMode();
  }

  @NotNull
  public GitObject<ObjectId> getObjectId() {
    return treeEntry.getObjectId();
  }

  @NotNull
  @Override
  public Map<String, String> getProperties(boolean includeInternalProps) throws IOException {
    final Map<String, String> props = new HashMap<>();
    for (GitProperty prop : this.props) {
      prop.apply(props);
    }
    final FileMode fileMode = treeEntry.getFileMode();
    if (fileMode.equals(FileMode.EXECUTABLE_FILE)) {
      props.put(SVNProperty.EXECUTABLE, "*");
    } else if (fileMode.equals(FileMode.SYMLINK)) {
      props.put(SVNProperty.SPECIAL, "*");
    }
    if (includeInternalProps) {
      final GitRevision last = getLastChange();
      props.put(SVNProperty.UUID, repo.getUuid());
      props.put(SVNProperty.COMMITTED_REVISION, String.valueOf(last.getId()));
      props.put(SVNProperty.COMMITTED_DATE, last.getDate());
      props.put(SVNProperty.LAST_AUTHOR, last.getAuthor());
    }
    return props;
  }

  @NotNull
  @Override
  public String getMd5() throws IOException, SVNException {
    return repo.getObjectMD5(treeEntry.getObjectId(), isSymlink() ? 'l' : 'f', this::openStream);
  }

  @Override
  public long getSize() throws IOException {
    if (isSymlink()) {
      return SvnConstants.LINK_PREFIX.length() + getObjectLoader().getSize();
    }
    return treeEntry.getFileMode().getObjectType() == Constants.OBJ_BLOB ? getObjectLoader().getSize() : 0;
  }

  @Override
  public boolean isDirectory() throws IOException {
    return getKind().equals(SVNNodeKind.DIR);
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() {
    final int objType = treeEntry.getFileMode().getObjectType();
    switch (objType) {
      case Constants.OBJ_TREE:
      case Constants.OBJ_COMMIT:
        return SVNNodeKind.DIR;
      case Constants.OBJ_BLOB:
        return SVNNodeKind.FILE;
      default:
        throw new IllegalStateException("Unknown obj type: " + objType);
    }
  }

  private ObjectLoader getObjectLoader() throws IOException {
    if (objectLoader == null) {
      objectLoader = treeEntry.getObjectId().openObject();
    }
    return objectLoader;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    if (isSymlink()) {
      try (
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SvnConstants.LINK_PREFIX.length() + (int) getObjectLoader().getSize());
          InputStream inputStream = getObjectLoader().openStream()
      ) {
        outputStream.write(SvnConstants.LINK_PREFIX.getBytes(StandardCharsets.ISO_8859_1));
        StreamHelper.copyTo(inputStream, outputStream);
        return new ByteArrayInputStream(outputStream.toByteArray());
      }
    }
    return getObjectLoader().openStream();
  }

  public boolean isSymlink() {
    return treeEntry.getFileMode() == FileMode.SYMLINK;
  }

  @NotNull
  private Iterable<GitTreeEntry> getRawEntries() throws IOException {
    if (rawEntriesCache == null) {
      rawEntriesCache = repo.loadTree(treeEntry);
    }
    return rawEntriesCache;
  }

  @NotNull
  @Override
  public Iterable<GitFile> getEntries() throws IOException, SVNException {
    if (treeEntriesCache == null) {
      final List<GitFile> result = new ArrayList<>();
      final String fullPath = getFullPath();
      for (GitTreeEntry entry : getRawEntries()) {
        result.add(new GitFile(repo, entry, fullPath, props, revision));
      }
      treeEntriesCache = result;
    }
    return treeEntriesCache;
  }

  @Nullable
  public GitFile getEntry(@NotNull String name) throws IOException, SVNException {
    for (GitTreeEntry entry : getRawEntries()) {
      if (entry.getFileName().equals(name)) {
        return new GitFile(repo, entry, getFullPath(), props, revision);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public GitRevision getLastChange() throws IOException {
    return repo.sureRevisionInfo(repo.getLastChange(getFullPath(), revision));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitFile that = (GitFile) o;
    return treeEntry.equals(that.treeEntry)
        && Arrays.equals(props, that.props);
  }

  @Override
  public int hashCode() {
    return treeEntry.hashCode();
  }

  @Override
  public String toString() {
    return "GitFileInfo{" +
        "fullPath='" + getFullPath() + '\'' +
        ", objectId=" + treeEntry.getId() +
        '}';
  }
}
