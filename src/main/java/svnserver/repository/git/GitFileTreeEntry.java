/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import svnserver.StringHelper;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.prop.GitProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFileTreeEntry implements GitFile {
  @NotNull
  private final GitRepository repo;
  @NotNull
  private final GitFilter filter;
  @NotNull
  private final GitProperty[] props;
  @NotNull
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

  public GitFileTreeEntry(@NotNull GitRepository repo, @NotNull GitTreeEntry treeEntry, @NotNull String parentPath, @NotNull GitProperty[] parentProps, int revision) throws IOException, SVNException {
    this.repo = repo;
    this.parentPath = parentPath;
    this.revision = revision;
    this.treeEntry = treeEntry;
    this.props = GitProperty.joinProperties(parentProps, treeEntry.getFileName(), treeEntry.getFileMode(), repo.collectProperties(treeEntry, this::getRawEntries));
    this.filter = repo.getFilter(treeEntry.getFileMode(), this.props);
  }

  public GitFileTreeEntry(@NotNull GitRepository repo, @NotNull RevCommit commit, int revisionId) throws IOException, SVNException {
    this(repo, new GitTreeEntry(repo.getRepository(), FileMode.TREE, commit.getTree(), ""), "", GitProperty.emptyArray, revisionId);
  }

  @NotNull
  @Override
  public GitRepository getRepo() {
    return repo;
  }

  @Override
  public int getRevision() {
    return revision;
  }

  @NotNull
  @Override
  public String getFileName() {
    return treeEntry.getFileName();
  }

  @NotNull
  @Override
  public GitProperty[] getRawProperties() {
    return props;
  }

  @NotNull
  public String getFullPath() {
    if (fullPathCache == null) {
      fullPathCache = StringHelper.joinPath(parentPath, getFileName());
    }
    return fullPathCache;
  }

  @NotNull
  public GitFilter getFilter() {
    return filter;
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
  public Map<String, String> getProperties() throws IOException, SVNException {
    final Map<String, String> props = getUpstreamProperties();
    final FileMode fileMode = getFileMode();
    if (fileMode.equals(FileMode.SYMLINK)) {
      props.put(SVNProperty.SPECIAL, "*");
    } else {
      if (fileMode.equals(FileMode.EXECUTABLE_FILE)) {
        props.put(SVNProperty.EXECUTABLE, "*");
      }
      if (fileMode.getObjectType() == Constants.OBJ_BLOB && repo.isObjectBinary(filter, getObjectId())) {
        props.put(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE);
      }
    }
    return props;
  }

  @NotNull
  @Override
  public String getMd5() throws IOException, SVNException {
    return filter.getMd5(treeEntry.getObjectId());
  }

  @NotNull
  @Override
  public String getContentHash() throws IOException, SVNException {
    return filter.getContentHash(treeEntry.getObjectId());
  }

  @Override
  public long getSize() throws IOException, SVNException {
    return isDirectory() ? 0L : filter.getSize(treeEntry.getObjectId());
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException, SVNException {
    return filter.inputStream(treeEntry.getObjectId());
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
        result.add(new GitFileTreeEntry(repo, entry, fullPath, props, revision));
      }
      treeEntriesCache = result;
    }
    return treeEntriesCache;
  }

  @Nullable
  public GitFileTreeEntry getEntry(@NotNull String name) throws IOException, SVNException {
    for (GitTreeEntry entry : getRawEntries()) {
      if (entry.getFileName().equals(name)) {
        return new GitFileTreeEntry(repo, entry, getFullPath(), props, revision);
      }
    }
    return null;
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
    GitFileTreeEntry that = (GitFileTreeEntry) o;
    return Objects.equals(treeEntry, that.treeEntry)
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
        ", objectId=" + treeEntry +
        '}';
  }
}
