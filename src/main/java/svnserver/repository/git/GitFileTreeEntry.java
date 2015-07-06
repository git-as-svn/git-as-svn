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
import org.eclipse.jgit.revwalk.RevTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsSupplier;
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
public class GitFileTreeEntry extends GitEntryImpl implements GitFile {
  @NotNull
  private final GitRepository repo;
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

  private GitFileTreeEntry(@NotNull GitRepository repo, @NotNull GitProperty[] parentProps, @NotNull String parentPath, @NotNull GitTreeEntry treeEntry, int revision, @NotNull EntriesCache entriesCache) throws IOException, SVNException {
    super(parentProps, parentPath, repo.collectProperties(treeEntry, entriesCache), treeEntry.getFileName(), treeEntry.getFileMode());
    this.repo = repo;
    this.revision = revision;
    this.treeEntry = treeEntry;
    this.entriesCache = entriesCache;
    this.filter = repo.getFilter(treeEntry.getFileMode(), this.getRawProperties());
  }

  @NotNull
  public static GitFile create(@NotNull GitRepository repo, @NotNull RevTree tree, int revision) throws IOException, SVNException {
    return create(repo, GitProperty.emptyArray, "", new GitTreeEntry(repo.getRepository(), FileMode.TREE, tree, ""), revision);
  }

  @NotNull
  private static GitFile create(@NotNull GitRepository repo, @NotNull GitProperty[] parentProps, @NotNull String parentPath, @NotNull GitTreeEntry treeEntry, int revision) throws IOException, SVNException {
    return new GitFileTreeEntry(repo, parentProps, parentPath, treeEntry, revision, new EntriesCache(repo, treeEntry));
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
  @Override
  public Iterable<GitFile> getEntries() throws IOException, SVNException {
    if (treeEntriesCache == null) {
      final List<GitFile> result = new ArrayList<>();
      final String fullPath = getFullPath();
      for (GitTreeEntry entry : entriesCache.get()) {
        result.add(GitFileTreeEntry.create(repo, getRawProperties(), fullPath, entry, revision));
      }
      treeEntriesCache = result;
    }
    return treeEntriesCache;
  }

  @Nullable
  public GitFile getEntry(@NotNull String name) throws IOException, SVNException {
    for (GitTreeEntry entry : entriesCache.get()) {
      if (entry.getFileName().equals(name)) {
        return create(repo, getRawProperties(), getFullPath(), entry, revision);
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
        && Arrays.equals(getRawProperties(), that.getRawProperties());
  }

  @Override
  public int hashCode() {
    return treeEntry.hashCode()
        + Arrays.hashCode(getRawProperties()) * 31;
  }

  @Override
  public String toString() {
    return "GitFileInfo{" +
        "fullPath='" + getFullPath() + '\'' +
        ", objectId=" + treeEntry +
        '}';
  }

  private static class EntriesCache implements VcsSupplier<Iterable<GitTreeEntry>> {
    private final GitRepository repo;
    private final GitTreeEntry treeEntry;
    public Iterable<GitTreeEntry> rawEntriesCache;

    public EntriesCache(GitRepository repo, GitTreeEntry treeEntry) {
      this.repo = repo;
      this.treeEntry = treeEntry;
    }

    @Override
    public Iterable<GitTreeEntry> get() throws SVNException, IOException {
      if (rawEntriesCache == null) {
        rawEntriesCache = repo.loadTree(treeEntry);
      }
      return rawEntriesCache;
    }
  }
}
