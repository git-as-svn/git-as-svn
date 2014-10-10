/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Top configuration object.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class Config {
  @NotNull
  private String host = "0.0.0.0";
  @NotNull
  private String realm = "";

  @NotNull
  private RepositoryConfig repository = new GitRepositoryConfig();

  @NotNull
  private UserDBConfig userDB = new LocalUserDBConfig();

  @NotNull
  private CacheConfig cacheConfig = new PersistentCacheConfig();

  @NotNull
  private AclConfig acl = new AclConfig();

  private int port = 3690;
  private boolean reuseAddress = false;
  private long shutdownTimeout = TimeUnit.SECONDS.toMillis(5);

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  @NotNull
  public String getHost() {
    return host;
  }

  public void setHost(@NotNull String host) {
    this.host = host;
  }

  @NotNull
  public String getRealm() {
    return realm;
  }

  public void setRealm(@NotNull String realm) {
    this.realm = realm.trim();
  }

  public void setRepository(@NotNull RepositoryConfig repository) {
    this.repository = repository;
  }

  @NotNull
  public RepositoryConfig getRepository() {
    return repository;
  }

  public void setUserDB(@NotNull UserDBConfig userDB) {
    this.userDB = userDB;
  }

  @NotNull
  public UserDBConfig getUserDB() {
    return userDB;
  }

  @NotNull
  public AclConfig getAcl() {
    return acl;
  }

  public void setAcl(@NotNull AclConfig acl) {
    this.acl = acl;
  }

  public boolean getReuseAddress() {
    return reuseAddress;
  }

  public void setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
  }

  public long getShutdownTimeout() {
    return shutdownTimeout;
  }

  public void setShutdownTimeout(long shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
  }

  @NotNull
  public CacheConfig getCacheConfig() {
    return cacheConfig;
  }

  public void setCacheConfig(@NotNull CacheConfig cacheConfig) {
    this.cacheConfig = cacheConfig;
  }
}
