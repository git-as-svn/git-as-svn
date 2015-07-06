/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.repository.VcsFile;
import svnserver.repository.git.prop.GitProperty;

import java.io.IOException;

/**
 * Simple GitEntry implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitEntryImpl implements GitEntry {
  @NotNull
  private final GitProperty[] props;
  @NotNull
  private final String name;
  @NotNull
  private final String parentPath;

  // Cache
  @Nullable
  private String fullPathCache;

  public GitEntryImpl(@NotNull GitProperty[] parentProps, @NotNull String parentPath, @NotNull GitProperty[] props, @NotNull String name, @NotNull FileMode fileMode) {
    this.parentPath = parentPath;
    this.name = name;
    this.props = GitProperty.joinProperties(parentProps, name, fileMode, props);
  }

  @NotNull
  @Override
  public GitProperty[] getRawProperties() {
    return props;
  }

  @NotNull
  @Override
  public String getFileName() {
    return name;
  }

  @NotNull
  @Override
  public String getFullPath() {
    if (fullPathCache == null) {
      fullPathCache = StringHelper.joinPath(parentPath, getFileName());
    }
    return fullPathCache;
  }

  @Nullable
  @Override
  public VcsFile getEntry(@NotNull String name) throws IOException, SVNException {
    return null;
  }

  @NotNull
  @Override
  public GitEntry createChild(@NotNull String name, boolean isDir) {
    return new GitEntryImpl(props, getFullPath(), GitProperty.emptyArray, name, isDir ? FileMode.TREE : FileMode.REGULAR_FILE);
  }
}
