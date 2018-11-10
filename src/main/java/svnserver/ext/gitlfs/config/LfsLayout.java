/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.config;

import org.jetbrains.annotations.NotNull;

/**
 * Git LFS file layout.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public enum LfsLayout {
  OneLevel {
    @NotNull
    @Override
    public String getPath(@NotNull String oid) {
      return oid.substring(0, 2) + "/" + oid;
    }
  },
  TwoLevels {
    @NotNull
    @Override
    public String getPath(@NotNull String oid) {
      return oid.substring(0, 2) + "/" + oid.substring(2, 4) + "/" + oid;
    }
  },
  GitLab {
    @NotNull
    @Override
    public String getPath(@NotNull String oid) {
      return oid.substring(0, 2) + "/" + oid.substring(2, 4) + "/" + oid.substring(4);
    }
  },
  HTTP {
    @Override
    public @NotNull String getPath(@NotNull String oid) {
      return oid;
    }
  };

  @NotNull
  public abstract String getPath(@NotNull String oid);
}
