/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache.config;

import com.google.common.cache.CacheBuilder;
import org.jetbrains.annotations.NotNull;
import svnserver.auth.UserDB;
import svnserver.auth.cache.CacheUserDB;
import svnserver.config.LocalUserDBConfig;
import svnserver.config.UserDBConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

import java.util.concurrent.TimeUnit;

/**
 * Authentication cache.
 * Can reduce authentication external service calls.
 *
 * @author Artem V. Navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("cacheUsers")
public final class CacheUserDBConfig implements UserDBConfig {
  /**
   * User database.
   */
  private UserDBConfig userDB = new LocalUserDBConfig();
  /**
   * Maximum cache items.
   */
  private long maximumSize = 10000;

  /**
   * Cache item expiration (ms).
   */
  private long expireTimeMs = 15000;

  @NotNull
  public UserDB create(@NotNull SharedContext context) {
    return new CacheUserDB(userDB.create(context), CacheBuilder.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterWrite(expireTimeMs, TimeUnit.MILLISECONDS)
        .build());
  }
}
