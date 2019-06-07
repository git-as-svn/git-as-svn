/*
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
@ConfigType("ldapUsers")
public final class LdapUserDBConfig implements UserDBConfig {
  /**
   * This is a URL whose format is defined by the JNDI provider.
   * It is usually an LDAP URL that specifies the domain name of the directory server to connect to,
   * and optionally the port number and distinguished name (DN) of the required root naming context.
   */
  @NotNull
  private String connectionUrl;

  /**
   * Bind configuration.
   */
  @NotNull
  private LdapBind bind;

  /**
   * Common part of search filter.
   */
  @NotNull
  private String searchFilter;

  /**
   * LDAP attribute, containing user login.
   */
  @NotNull
  private String loginAttribute;

  /**
   * LDAP attribute, containing user name.
   */
  @NotNull
  private String nameAttribute;

  /**
   * LDAP attribute, containing user email.
   */
  @NotNull
  private String emailAttribute;

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
  private String fakeMailSuffix;

  /**
   * Maximum LDAP connections.
   */
  private int maxConnections;

  public LdapUserDBConfig() {
    this(
        "ldap://localhost:389/ou=groups,dc=mycompany,dc=com",
        LdapBindANONYMOUS.instance,
        "",
        "sAMAccountName",
        "name",
        "mail",
        null,
        10,
        ""
    );
  }

  public LdapUserDBConfig(@NotNull String connectionUrl,
                          @NotNull LdapBind bind,
                          @NotNull String searchFilter,
                          @NotNull String loginAttribute,
                          @NotNull String nameAttribute,
                          @NotNull String emailAttribute,
                          @Nullable String ldapCertPem,
                          int maxConnections,
                          @NotNull String fakeMailSuffix) {
    this.connectionUrl = connectionUrl;
    this.bind = bind;
    this.searchFilter = searchFilter;
    this.loginAttribute = loginAttribute;
    this.nameAttribute = nameAttribute;
    this.emailAttribute = emailAttribute;
    this.ldapCertPem = ldapCertPem;
    this.maxConnections = maxConnections;
    this.fakeMailSuffix = fakeMailSuffix;
  }

  @NotNull
  public String getLoginAttribute() {
    return loginAttribute;
  }

  @NotNull
  public String getEmailAttribute() {
    return emailAttribute;
  }

  @NotNull
  public String getNameAttribute() {
    return nameAttribute;
  }

  @NotNull
  public String getSearchFilter() {
    return searchFilter;
  }

  @NotNull
  public LdapBind getBind() {
    return bind;
  }

  @NotNull
  public String getConnectionUrl() {
    return connectionUrl;
  }

  @Nullable
  public String getLdapCertPem() {
    return ldapCertPem;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  @NotNull
  public String getFakeMailSuffix() {
    return fakeMailSuffix;
  }

  @NotNull
  @Override
  public UserDB create(@NotNull SharedContext context) throws Exception {
    return new LdapUserDB(context, this);
  }
}
