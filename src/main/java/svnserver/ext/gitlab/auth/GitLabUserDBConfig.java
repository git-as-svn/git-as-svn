/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.auth;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.UserDB;
import svnserver.config.UserDBConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

/**
 * GitLab authentication configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("gitlabUsers")
public final class GitLabUserDBConfig implements UserDBConfig {
  @NotNull
  @Override
  public UserDB create(@NotNull SharedContext context) {
    return new GitLabUserDB(context);
  }
}
