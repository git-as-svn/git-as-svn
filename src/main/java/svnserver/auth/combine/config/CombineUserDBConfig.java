/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.combine.config;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.UserDB;
import svnserver.auth.combine.CombineUserDB;
import svnserver.config.UserDBConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

import java.util.Arrays;

/**
 * Complex authentication.
 * Can combine multiple authenticators.
 *
 * @author Artem V. Navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("combineUsers")
public final class CombineUserDBConfig implements UserDBConfig {
  /**
   * Combined user databases.
   */
  @NotNull
  private UserDBConfig[] items = UserDBConfig.emptyArray;

  @NotNull
  @Override
  public UserDB create(@NotNull SharedContext context) {
    return new CombineUserDB(Arrays.stream(items)
        .map(item -> item.create(context))
        .toArray(UserDB[]::new)
    );
  }
}
