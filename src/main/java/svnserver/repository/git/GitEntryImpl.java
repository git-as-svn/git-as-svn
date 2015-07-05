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
import svnserver.repository.git.prop.GitProperty;

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

  public GitEntryImpl(@NotNull GitProperty[] parentProps, @NotNull String name, boolean isDir) {
    this.name = name;
    this.props = GitProperty.joinProperties(parentProps, name, isDir ? FileMode.TREE : FileMode.REGULAR_FILE, GitProperty.emptyArray);
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
  public GitEntry createChild(@NotNull String name, boolean isDir) {
    return new GitEntryImpl(props, name, isDir);
  }
}
