/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.prop.GitProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFileEmptyTree extends GitEntryImpl implements GitFile {
  @NotNull
  private final GitRepository repo;

  private final int revision;

  public GitFileEmptyTree(@NotNull GitRepository repo, @NotNull String parentPath, int revision) {
    super(GitProperty.emptyArray, parentPath, GitProperty.emptyArray, "", FileMode.TREE);
    this.repo = repo;
    this.revision = revision;
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

  @Nullable
  public GitFilter getFilter() {
    return null;
  }

  @Nullable
  public GitTreeEntry getTreeEntry() {
    return null;
  }

  @NotNull
  public FileMode getFileMode() {
    return FileMode.TREE;
  }

  @Nullable
  @Override
  public GitFile getEntry(@NotNull String name) throws IOException, SVNException {
    return null;
  }

  @Nullable
  public GitObject<ObjectId> getObjectId() {
    return null;
  }

  @NotNull
  @Override
  public String getMd5() throws IOException, SVNException {
    throw new IllegalStateException("Can't get md5 without object.");
  }

  @NotNull
  @Override
  public String getContentHash() throws IOException, SVNException {
    throw new IllegalStateException("Can't get content hash without object.");
  }

  @Override
  public long getSize() throws IOException, SVNException {
    return 0L;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException, SVNException {
    throw new IllegalStateException("Can't get open stream without object.");
  }

  @NotNull
  @Override
  public Iterable<GitFile> getEntries() throws IOException, SVNException {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public VcsCopyFrom getCopyFrom() throws IOException {
    return getLastChange().getCopyFrom(getFullPath());
  }

  @Override
  public String toString() {
    return "GitFileEmptyTree{" +
        "fullPath='" + getFullPath() + '\'' +
        '}';
  }
}
