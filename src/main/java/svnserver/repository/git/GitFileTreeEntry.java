/*
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
import org.eclipse.jgit.revwalk.RevTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsSupplier;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.prop.GitProperty;
import svnserver.repository.git.prop.PropertyMapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static ru.bozaro.gitlfs.common.Constants.MIME_BINARY;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitFileTreeEntry extends GitEntryImpl implements GitFile {
  @NotNull
  private final GitBranch branch;
  @NotNull
  private final GitFilter filter;
  @NotNull
  private final GitTreeEntry treeEntry;

  private final int revision;

  // Cache
  @NotNull
  private final EntriesCache entriesCache;
  @Nullable
  private Iterable<GitFile> treeEntriesCache;

  private GitFileTreeEntry(@NotNull GitBranch branch, @NotNull GitProperty[] parentProps, @NotNull String parentPath, @NotNull GitTreeEntry treeEntry, int revision, @NotNull EntriesCache entriesCache) throws IOException, SVNException {
    super(parentProps, parentPath, branch.getRepository().collectProperties(treeEntry, entriesCache), treeEntry.getFileName(), treeEntry.getFileMode());
    this.branch = branch;
    this.revision = revision;
    this.treeEntry = treeEntry;
    this.entriesCache = entriesCache;
    this.filter = branch.getRepository().getFilter(treeEntry.getFileMode(), this.getRawProperties());
  }

  @NotNull
  public static GitFile create(@NotNull GitBranch branch, @NotNull RevTree tree, int revision) throws IOException, SVNException {
    return create(branch, PropertyMapping.getRootProperties(), "", new GitTreeEntry(branch.getRepository().getGit(), FileMode.TREE, tree, ""), revision);
  }

  @NotNull
  private static GitFile create(@NotNull GitBranch branch, @NotNull GitProperty[] parentProps, @NotNull String parentPath, @NotNull GitTreeEntry treeEntry, int revision) throws IOException, SVNException {
    return new GitFileTreeEntry(branch, parentProps, parentPath, treeEntry, revision, new EntriesCache(branch.getRepository(), treeEntry));
  }

  @NotNull
  @Override
  public String getContentHash() {
    return filter.getContentHash(treeEntry.getObjectId());
  }

  @NotNull
  @Override
  public String getMd5() throws IOException, SVNException {
    return filter.getMd5(treeEntry.getObjectId());
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

  @Nullable
  @Override
  public VcsCopyFrom getCopyFrom() {
    return getLastChange().getCopyFrom(getFullPath());
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
  public GitObject<ObjectId> getObjectId() {
    return treeEntry.getObjectId();
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() throws IOException, SVNException {
    final Map<String, String> props = getUpstreamProperties();
    final FileMode fileMode = getFileMode();
    if (fileMode.equals(FileMode.SYMLINK)) {
      props.remove(SVNProperty.EOL_STYLE);
      props.remove(SVNProperty.MIME_TYPE);
      props.put(SVNProperty.SPECIAL, "*");
    } else {
      if (fileMode.equals(FileMode.EXECUTABLE_FILE)) {
        props.put(SVNProperty.EXECUTABLE, "*");
      }
      if (fileMode.getObjectType() == Constants.OBJ_BLOB && branch.getRepository().isObjectBinary(filter, getObjectId())) {
        props.remove(SVNProperty.EOL_STYLE);
        props.put(SVNProperty.MIME_TYPE, MIME_BINARY);
      }
    }
    return props;
  }

  @NotNull
  @Override
  public GitBranch getBranch() {
    return branch;
  }

  @Override
  public int getRevision() {
    return revision;
  }

  @NotNull
  public FileMode getFileMode() {
    return treeEntry.getFileMode();
  }

  @NotNull
  @Override
  public Iterable<GitFile> getEntries() throws IOException, SVNException {
    if (treeEntriesCache == null) {
      final List<GitFile> result = new ArrayList<>();
      final String fullPath = getFullPath();
      for (GitTreeEntry entry : entriesCache.get()) {
        result.add(GitFileTreeEntry.create(branch, getRawProperties(), fullPath, entry, revision));
      }
      treeEntriesCache = result;
    }
    return treeEntriesCache;
  }

  @Nullable
  public GitFile getEntry(@NotNull String name) throws IOException, SVNException {
    for (GitTreeEntry entry : entriesCache.get()) {
      if (entry.getFileName().equals(name)) {
        return create(branch, getRawProperties(), getFullPath(), entry, revision);
      }
    }
    return null;
  }

  @Override
  public int hashCode() {
    return treeEntry.hashCode()
        + Arrays.hashCode(getRawProperties()) * 31;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitFileTreeEntry that = (GitFileTreeEntry) o;
    return Objects.equals(treeEntry, that.treeEntry)
        && Arrays.equals(getRawProperties(), that.getRawProperties());
  }

  @Override
  public String toString() {
    return "GitFileInfo{" +
        "fullPath='" + getFullPath() + '\'' +
        ", objectId=" + treeEntry +
        '}';
  }

  private static class EntriesCache implements VcsSupplier<Iterable<GitTreeEntry>> {
    @NotNull
    private final GitRepository repo;
    @NotNull
    private final GitTreeEntry treeEntry;
    @Nullable
    private Iterable<GitTreeEntry> rawEntriesCache;

    private EntriesCache(@NotNull GitRepository repo, @NotNull GitTreeEntry treeEntry) {
      this.repo = repo;
      this.treeEntry = treeEntry;
    }

    @Override
    @NotNull
    public Iterable<GitTreeEntry> get() throws IOException {
      if (rawEntriesCache == null) {
        rawEntriesCache = repo.loadTree(treeEntry);
      }
      return rawEntriesCache;
    }
  }
}
