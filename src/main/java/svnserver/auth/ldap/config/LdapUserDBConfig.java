/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.auth.UserDB;
import svnserver.auth.ldap.LdapUserDB;
import svnserver.config.UserDBConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("ldapUsers")
public final class LdapUserDBConfig implements UserDBConfig {
  /**
   * This is a URL whose format is defined by the JNDI provider.
   * It is usually an LDAP URL that specifies the domain name of the directory server to connect to,
   * and optionally the port number and distinguished name (DN) of the required root naming context.
   */
  @NotNull
  private String connectionUrl = "ldap://localhost:389/ou=groups,dc=mycompany,dc=com";

  /**
   * Bind configuration.
   */
  @Nullable
  private LdapBind bind;

  /**
   * Common part of search filter.
   */
  @NotNull
  private String searchFilter = "";

  /**
   * LDAP attribute, containing user login.
   */
  @NotNull
  private String loginAttribute = "sAMAccountName";

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

  /**
   * Email addresses suffix for users without LDAP email.
   * If empty - don't generate emails.
   */
  @NotNull
  private String fakeMailSuffix = "";

  /**
   * Maximum LDAP connections.
   */
  private int maxConnections = 10;

  @NotNull
  public String getLoginAttribute() {
    return loginAttribute;
  }

  public void setLoginAttribute(@NotNull String loginAttribute) {
    this.loginAttribute = loginAttribute;
  }

  @NotNull
  public String getEmailAttribute() {
    return emailAttribute;
  }

  public void setEmailAttribute(@NotNull String emailAttribute) {
    this.emailAttribute = emailAttribute;
  }

  @NotNull
  public String getNameAttribute() {
    return nameAttribute;
  }

  public void setNameAttribute(@NotNull String nameAttribute) {
    this.nameAttribute = nameAttribute;
  }

  @NotNull
  public String getSearchFilter() {
    return searchFilter;
  }

  public void setSearchFilter(@NotNull String searchFilter) {
    this.searchFilter = searchFilter;
  }

  @Nullable
  public LdapBind getBind() {
    return bind;
  }

  public void setBind(@Nullable LdapBind bind) {
    this.bind = bind;
  }

  @NotNull
  public String getConnectionUrl() {
    return connectionUrl;
  }

  public void setConnectionUrl(@NotNull String connectionUrl) {
    this.connectionUrl = connectionUrl;
  }

  @Nullable
  public String getLdapCertPem() {
    return ldapCertPem;
  }

  public void setLdapCertPem(@Nullable String ldapCertPem) {
    this.ldapCertPem = ldapCertPem;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  @NotNull
  public String getFakeMailSuffix() {
    return fakeMailSuffix;
  }

  public void setFakeMailSuffix(@NotNull String fakeMailSuffix) {
    this.fakeMailSuffix = fakeMailSuffix;
  }

  @NotNull
  @Override
  public UserDB create(@NotNull SharedContext context) {
    return new LdapUserDB(context, this);
  }
}
