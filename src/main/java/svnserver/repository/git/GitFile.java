package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFile implements VcsFile {
  @FunctionalInterface
  public interface PropertyResolver {
    public GitProperty[] getProperties(@NotNull GitTreeEntry object) throws IOException;
  }

  @NotNull
  private final GitRepository repo;
  @NotNull
  private final PropertyResolver resolver;
  @NotNull
  private final GitProperty[] props;
  @NotNull
  private final GitTreeEntry treeEntry;
  @NotNull
  private final String fullPath;

  private final int revision;

  // Cache
  @Nullable
  private ObjectLoader objectLoader;
  @Nullable
  private Map<String, VcsFile> cacheEntries;

  public GitFile(@NotNull GitRepository repo, @NotNull GitTreeEntry treeEntry, @NotNull String fullPath, @NotNull GitProperty[] parentProps, int revision, @NotNull PropertyResolver resolver) throws IOException {
    this.repo = repo;
    this.treeEntry = treeEntry;
    this.fullPath = fullPath;
    this.props = GitProperty.joinProperties(parentProps, StringHelper.baseName(fullPath), treeEntry.getFileMode(), resolver.getProperties(treeEntry));
    this.resolver = resolver;
    this.revision = revision;
  }

  @NotNull
  @Override
  public String getFileName() {
    return StringHelper.baseName(fullPath);
  }

  @NotNull
  public String getFullPath() {
    return fullPath;
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
  public String getMd5() throws IOException {
    return repo.getObjectMD5(treeEntry.getObjectId(), treeEntry.getFileMode() == FileMode.SYMLINK ? 'l' : 'f', this::openStream);
  }

  @Override
  public long getSize() throws IOException {
    final FileMode fileMode = treeEntry.getFileMode();
    if (fileMode == FileMode.SYMLINK) {
      return SvnConstants.LINK_PREFIX.length() + getObjectLoader().getSize();
    }
    return fileMode.getObjectType() == Constants.OBJ_BLOB ? getObjectLoader().getSize() : 0;
  }

  @Override
  public boolean isDirectory() throws IOException {
    return getKind().equals(SVNNodeKind.DIR);
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() {
    return GitHelper.getKind(treeEntry.getFileMode());
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
    if (treeEntry.getFileMode() == FileMode.SYMLINK) {
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

  @NotNull
  @Override
  public Map<String, VcsFile> getEntries() throws IOException {
    if (cacheEntries == null) {
      final Map<String, VcsFile> result = new TreeMap<>();
      for (Map.Entry<String, GitTreeEntry> entry : repo.loadTree(treeEntry).entrySet()) {
        result.put(entry.getKey(), new GitFile(repo, entry.getValue(), StringHelper.joinPath(fullPath, entry.getKey()), props, revision, resolver));
      }
      cacheEntries = result;
    }
    return cacheEntries;
  }

  @NotNull
  @Override
  public GitRevision getLastChange() throws IOException {
    return repo.sureRevisionInfo(repo.getLastChange(fullPath, revision));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitFile that = (GitFile) o;
    return treeEntry.equals(that.treeEntry)
        && fullPath.equals(that.fullPath)
        && Arrays.equals(props, that.props);
  }

  @Override
  public int hashCode() {
    int result = treeEntry.hashCode();
    result = 31 * result + fullPath.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "GitFileInfo{" +
        "fullPath='" + fullPath + '\'' +
        ", objectId=" + treeEntry.getId() +
        '}';
  }
}
