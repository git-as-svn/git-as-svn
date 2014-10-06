/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.StringHelper;

import java.io.IOException;

/**
 * Send copy from type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public enum SendCopyFrom {
  /**
   * Never send copy from. Always send full file.
   */
  Never {
    @Nullable
    @Override
    public VcsCopyFrom getCopyFrom(@NotNull String basePath, @NotNull VcsFile file) {
      return null;
    }
  },
  /**
   * Always send copy from information. Always send delta with copy-from file.
   */
  Always {
    @Nullable
    @Override
    public VcsCopyFrom getCopyFrom(@NotNull String basePath, @NotNull VcsFile file) throws IOException {
      return file.getCopyFrom();
    }
  },
  /**
   * Send copy from information only if file copied from subpath of basePath.
   */
  OnlyRelative {
    @Nullable
    @Override
    public VcsCopyFrom getCopyFrom(@NotNull String basePath, @NotNull VcsFile file) throws IOException {
      final VcsCopyFrom copyFrom = file.getCopyFrom();
      return copyFrom != null && StringHelper.isParentPath(basePath, copyFrom.getPath()) ? copyFrom : null;
    }
  };

  @Nullable
  public abstract VcsCopyFrom getCopyFrom(@NotNull String basePath, @NotNull VcsFile file) throws IOException;
}
