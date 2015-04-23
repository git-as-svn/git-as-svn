/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import com.unboundid.ldap.sdk.DIGESTMD5BindRequest;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.LDAPUserDBConfig;

import javax.naming.NamingException;
import javax.net.SocketFactory;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;

/**
 * Authenticates a user by binding to the directory with the DN of the entry for that user and the password
 * presented by the user. If this simple bind succeeds the user is considered to be authenticated.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LDAPUserDB implements UserDB, PasswordChecker {

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LDAPUserDB.class);

  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));

  @NotNull
  private final LDAPUserDBConfig config;
  @NotNull
  private final String baseDn;
  @NotNull
  private final String ldapHost;
  private final int ldapPort;
  @Nullable
  private final SocketFactory socketFactory;

  public LDAPUserDB(@NotNull LDAPUserDBConfig config) {
    URI ldapUri = URI.create(config.getConnectionUrl());
    SocketFactory factory;
    int defaultPort;
    switch (ldapUri.getScheme().toLowerCase()) {
      case "ldap":
        factory = null;
        defaultPort = 389;
        break;
      case "ldaps":
        factory = createSslFactory(config);
        defaultPort = 636;
        break;
      default:
        throw new IllegalStateException("Unknown ldap scheme: " + ldapUri.getScheme());
    }
    this.socketFactory = factory;
    this.baseDn = ldapUri.getPath().isEmpty() ? "" : ldapUri.getPath().substring(1);
    this.config = config;
    this.ldapPort = ldapUri.getPort() > 0 ? ldapUri.getPort() : defaultPort;
    this.ldapHost = ldapUri.getHost();
  }

  private static SocketFactory createSslFactory(@NotNull LDAPUserDBConfig config) {
    try {
      return new SSLUtil(null, new TrustAllTrustManager()).createSSLSocketFactory();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Can't create SSL Socket Factory", e);
    }
  }

  @Nullable
  @Override
  public User check(@NotNull String username, @NotNull String password) throws SVNException {
    try {
      LDAPConnection ldap = new LDAPConnection(socketFactory, ldapHost, ldapPort);
      try {
        ldap.bind(new DIGESTMD5BindRequest(username, password));
        com.unboundid.ldap.sdk.SearchResult search = ldap.search(
            baseDn,
            config.isUserSubtree() ? com.unboundid.ldap.sdk.SearchScope.SUB : com.unboundid.ldap.sdk.SearchScope.ONE,
            MessageFormat.format(config.getUserSearch(), username),
            config.getNameAttribute(), config.getEmailAttribute()
        );
        if (search.getEntryCount() == 0) {
          log.debug("Failed to find LDAP entry for {}", username);
          return null;
        } else if (search.getEntryCount() > 1) {
          log.error("Multiple LDAP entries found for {}", username);
          return null;
        }
        final com.unboundid.ldap.sdk.SearchResultEntry entry = search.getSearchEntries().get(0);
        final String realName = getAttribute(entry, config.getNameAttribute());
        final String email = getAttribute(entry, config.getEmailAttribute());
        return new User(username, realName != null ? realName : username, email);
      } finally {
        ldap.close();
      }
    } catch (LDAPException e) {
      if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
        return null;
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, e.getMessage()), e);
    } catch (NamingException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, e.getMessage()), e);
    }
  }

  @Nullable
  private String getAttribute(@NotNull com.unboundid.ldap.sdk.SearchResultEntry entry, @NotNull String name) throws NamingException {
    com.unboundid.ldap.sdk.Attribute attribute = entry.getAttribute(name);
    return attribute == null ? null : attribute.getValue();
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }
}
