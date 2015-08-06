/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.auth.LDAPUserDB;
import svnserver.auth.UserDB;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("ldapUsers")
public final class LDAPUserDBConfig implements UserDBConfig {

  /**
   * This is a URL whose format is defined by the JNDI provider.
   * It is usually an LDAP URL that specifies the domain name of the directory server to connect to,
   * and optionally the port number and distinguished name (DN) of the required root naming context.
   */
  @NotNull
  private String connectionUrl = "ldap://localhost:389/ou=groups,dc=mycompany,dc=com";
  /**
   * The search scope. Set to <code>true</code> if you wish to search the entire subtree rooted at the
   * <code>userBase</code> entry. The default value of <code>false</code> requests a single-level search
   * including only the top level.
   */
  private boolean userSubtree;
  /**
   * Pattern specifying the LDAP search filter to use after substitution of the username.
   */
  @NotNull
  private String userSearch = "(samaccountname={0})";
  /**
   * LDAP attribute, containing user name.
   */
  @NotNull
  private String nameAttribute = "name";
  /**
   * LDAP attribute, containing user email.
   */
  @NotNull
  private String emailAttribute = "mail";
  /**
   * Certificate for validation LDAP server with SSL connection.
   */
  @Nullable
  private String ldapCertPem;

  @NotNull
  public String getConnectionUrl() {
    return connectionUrl;
  }

  public void setConnectionUrl(@NotNull String connectionUrl) {
    this.connectionUrl = connectionUrl;
  }

  public boolean isUserSubtree() {
    return userSubtree;
  }

  public void setUserSubtree(boolean userSubtree) {
    this.userSubtree = userSubtree;
  }

  @NotNull
  public String getUserSearch() {
    return userSearch;
  }

  public void setUserSearch(@NotNull String userSearch) {
    this.userSearch = userSearch;
  }

  @NotNull
  public String getNameAttribute() {
    return nameAttribute;
  }

  public void setNameAttribute(@NotNull String nameAttribute) {
    this.nameAttribute = nameAttribute;
  }

  @NotNull
  public String getEmailAttribute() {
    return emailAttribute;
  }

  @Nullable
  public String getLdapCertPem() {
    return ldapCertPem;
  }

  public void setLdapCertPem(@Nullable String ldapCertPem) {
    this.ldapCertPem = ldapCertPem;
  }

  @NotNull
  @Override
  public UserDB create(@NotNull SharedContext context) {
    return new LDAPUserDB(this, context.getBasePath());
  }
}
