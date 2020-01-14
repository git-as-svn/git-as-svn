/*
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
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.prop.GitProperty;

import java.io.InputStream;
import java.util.Collections;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitFileEmptyTree extends GitEntryImpl implements GitFile {
  @NotNull
  private final GitBranch branch;

  private final int revision;

  GitFileEmptyTree(@NotNull GitBranch branch, @NotNull String parentPath, int revision) {
    super(GitProperty.emptyArray, parentPath, GitProperty.emptyArray, "", FileMode.TREE);
    this.branch = branch;
    this.revision = revision;
  }

  @Nullable
  @Override
  public GitFile getEntry(@NotNull String name) {
    return null;
  }

  @NotNull
  @Override
  public String getContentHash() {
    throw new IllegalStateException("Can't get content hash without object.");
  }

  @NotNull
  @Override
  public String getMd5() {
    throw new IllegalStateException("Can't get md5 without object.");
  }

  @Override
  public long getSize() {
    return 0L;
  }

  @NotNull
  @Override
  public InputStream openStream() {
    throw new IllegalStateException("Can't get open stream without object.");
  }

  @Nullable
  @Override
  public VcsCopyFrom getCopyFrom() {
    return getLastChange().getCopyFrom(getFullPath());
  }

  @Nullable
  public GitFilter getFilter() {
    return null;
  }

  @Nullable
  public GitTreeEntry getTreeEntry() {
    return null;
  }

  @Nullable
  public GitObject<ObjectId> getObjectId() {
    return null;
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
    return FileMode.TREE;
  }

  @NotNull
  @Override
  public Iterable<GitFile> getEntries() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "GitFileEmptyTree{" +
        "fullPath='" + getFullPath() + '\'' +
        '}';
  }
}
