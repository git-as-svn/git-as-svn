/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import svnserver.StreamHelper;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.TemporaryOutputStream;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsFile;
import svnserver.repository.git.prop.GitProperty;

import java.io.ByteArrayInputStream;
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
  public static final byte[] emptyBytes = new byte[0];
  @NotNull
  private final GitRepository repo;
  @NotNull
  private final GitProperty[] props;
  @Nullable
  private final GitTreeEntry treeEntry;
  @NotNull
  private final String parentPath;

  private final int revision;

  // Cache
  @Nullable
  private Iterable<GitTreeEntry> rawEntriesCache;
  @Nullable
  private Iterable<GitFile> treeEntriesCache;
  @Nullable
  private String fullPathCache;

  public GitFile(@NotNull GitRepository repo, @Nullable GitTreeEntry treeEntry, @NotNull String parentPath, @NotNull GitProperty[] parentProps, int revision) throws IOException, SVNException {
    this.repo = repo;
    this.parentPath = parentPath;
    this.revision = revision;
    if (treeEntry != null) {
      this.treeEntry = treeEntry;
      this.props = GitProperty.joinProperties(parentProps, treeEntry.getFileName(), treeEntry.getFileMode(), repo.collectProperties(treeEntry, this::getRawEntries));
    } else {
      this.treeEntry = null;
      this.props = GitProperty.emptyArray;
    }
  }

  public GitFile(@NotNull GitRepository repo, @NotNull RevCommit commit, int revisionId) throws IOException, SVNException {
    this(repo, new GitTreeEntry(repo.getRepository(), FileMode.TREE, commit.getTree(), ""), "", GitProperty.emptyArray, revisionId);
  }

  @NotNull
  @Override
  public String getFileName() {
    return treeEntry == null ? "" : treeEntry.getFileName();
  }

  @NotNull
  public String getFullPath() {
    if (fullPathCache == null) {
      fullPathCache = StringHelper.joinPath(parentPath, getFileName());
    }
    return fullPathCache;
  }

  @Nullable
  public GitTreeEntry getTreeEntry() {
    return treeEntry;
  }

  @NotNull
  public FileMode getFileMode() {
    return treeEntry == null ? FileMode.TREE : treeEntry.getFileMode();
  }

  @Nullable
  public GitObject<ObjectId> getObjectId() {
    return treeEntry == null ? null : treeEntry.getObjectId();
  }

  public Map<String, String> getUpstreamProperties() {
    final Map<String, String> result = new HashMap<>();
    for (GitProperty prop : props) {
      prop.apply(result);
    }
    return result;
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() throws IOException {
    final Map<String, String> props = getUpstreamProperties();
    final FileMode fileMode = getFileMode();
    if (fileMode.equals(FileMode.SYMLINK)) {
      props.put(SVNProperty.SPECIAL, "*");
    } else {
      if (fileMode.equals(FileMode.EXECUTABLE_FILE)) {
        props.put(SVNProperty.EXECUTABLE, "*");
      }
      if (fileMode.getObjectType() == Constants.OBJ_BLOB && repo.isObjectBinary(getObjectId())) {
        props.put(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE);
      }
    }
    return props;
  }

  @NotNull
  @Override
  public Map<String, String> getRevProperties() throws IOException {
    final Map<String, String> props = new HashMap<>();
    final GitRevision last = getLastChange();
    props.put(SVNProperty.UUID, repo.getUuid());
    props.put(SVNProperty.COMMITTED_REVISION, String.valueOf(last.getId()));
    putProperty(props, SVNProperty.COMMITTED_DATE, last.getDateString());
    putProperty(props, SVNProperty.LAST_AUTHOR, last.getAuthor());
    return props;
  }

  private void putProperty(@NotNull Map<String, String> props, @NotNull String name, @Nullable String value) {
    if (value != null) {
      props.put(name, value);
    }
  }

  @NotNull
  @Override
  public String getMd5() throws IOException, SVNException {
    if (treeEntry == null || isDirectory()) {
      throw new IllegalStateException("Can't get md5 from directory.");
    }
    return repo.getObjectMD5(treeEntry.getObjectId(), isSymlink() ? 'l' : 'f', this::openStream);
  }

  @NotNull
  @Override
  public String getContentHash() throws IOException, SVNException {
    if (treeEntry == null || isDirectory()) {
      throw new IllegalStateException("Can't compare content from directory.");
    }
    return treeEntry.getObjectId().getObject().name() + (isSymlink() ? 'l' : 'f');
  }

  @Override
  public long getSize() throws IOException, SVNException {
    if (getFileMode().getObjectType() != Constants.OBJ_BLOB)
      return 0L;

    if (treeEntry == null) {
      throw new IllegalStateException("Can't get size without object.");
    }

    final GitObject<ObjectId> objectId = treeEntry.getObjectId();
    ObjectReader reader = objectId.getRepo().newObjectReader();
    long objectSize = reader.getObjectSize(objectId.getObject(), treeEntry.getFileMode().getObjectType());
    return objectSize + (isSymlink() ? 0 : SvnConstants.LINK_PREFIX.length());
  }

  @Override
  public boolean isDirectory() {
    return getKind().equals(SVNNodeKind.DIR);
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() {
    final int objType = getFileMode().getObjectType();
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

  @Nullable
  private ObjectLoader getObjectLoader() throws IOException {
    return treeEntry != null ? treeEntry.getObjectId().openObject() : null;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    final ObjectLoader loader = getObjectLoader();
    if (loader == null) {
      return new ByteArrayInputStream(emptyBytes);
    }
    if (isSymlink()) {
      try (
          TemporaryOutputStream outputStream = new TemporaryOutputStream();
          InputStream inputStream = loader.openStream()
      ) {
        outputStream.write(SvnConstants.LINK_PREFIX.getBytes(StandardCharsets.ISO_8859_1));
        StreamHelper.copyTo(inputStream, outputStream);
        return outputStream.toInputStream();
      }
    }
    return loader.openStream();
  }

  public boolean isSymlink() {
    return getFileMode() == FileMode.SYMLINK;
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
    final int lastChange = repo.getLastChange(getFullPath(), revision);
    if (lastChange < 0) {
      throw new IllegalStateException("Internal error: can't find lastChange revision for file: " + getFileName() + "@" + revision);
    }
    return repo.sureRevisionInfo(lastChange);
  }

  @Nullable
  @Override
  public VcsCopyFrom getCopyFrom() throws IOException {
    return getLastChange().getCopyFrom(getFullPath());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitFile that = (GitFile) o;
    return Objects.equals(treeEntry, that.treeEntry)
        && Arrays.equals(props, that.props);
  }

  @Override
  public int hashCode() {
    return treeEntry == null ? 0 : treeEntry.hashCode();
  }

  @Override
  public String toString() {
    return "GitFileInfo{" +
        "fullPath='" + getFullPath() + '\'' +
        ", objectId=" + treeEntry +
        '}';
  }
}
