/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.serializer.ConfigType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Top configuration object.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("config")
public final class Config {
  @NotNull
  private String host = "0.0.0.0";
  @NotNull
  private String realm = "";

  @NotNull
  private RepositoryMappingConfig repositoryMapping = new RepositoryListMappingConfig();

  @NotNull
  private UserDBConfig userDB = new LocalUserDBConfig();

  @NotNull
  private CacheConfig cacheConfig = new PersistentCacheConfig();

  @NotNull
  private AclConfig acl = new AclConfig();

  @NotNull
  private List<SharedConfig> shared = new ArrayList<>();

  private int port = 3690;
  private boolean reuseAddress = false;
  private long shutdownTimeout = TimeUnit.SECONDS.toMillis(5);

  @SuppressWarnings("UnusedDeclaration")
  public Config() {
  }

  public Config(@NotNull String host, int port) {
    this.host = host;
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  @NotNull
  public String getHost() {
    return host;
  }

  @NotNull
  public String getRealm() {
    return realm;
  }

  @NotNull
  public RepositoryMappingConfig getRepositoryMapping() {
    return repositoryMapping;
  }

  public void setRepositoryMapping(@NotNull RepositoryMappingConfig repositoryMapping) {
    this.repositoryMapping = repositoryMapping;
  }

  @NotNull
  public UserDBConfig getUserDB() {
    return userDB;
  }

  public void setUserDB(@NotNull UserDBConfig userDB) {
    this.userDB = userDB;
  }

  @NotNull
  public AclConfig getAcl() {
    return acl;
  }

  public boolean getReuseAddress() {
    return reuseAddress;
  }

  public long getShutdownTimeout() {
    return shutdownTimeout;
  }

  @NotNull
  public CacheConfig getCacheConfig() {
    return cacheConfig;
  }

  public void setCacheConfig(@NotNull CacheConfig cacheConfig) {
    this.cacheConfig = cacheConfig;
  }

  @NotNull
  public List<SharedConfig> getShared() {
    return shared;
  }
}
