/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap;

import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.SSLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.Authenticator;
import svnserver.auth.PlainAuthenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.auth.ldap.config.LdapUserDBConfig;
import svnserver.config.ConfigHelper;
import svnserver.context.SharedContext;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * LDAP authentication.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LdapUserDB implements UserDB {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LdapUserDB.class);

  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));
  @NotNull
  private final LDAPConnectionPool pool;
  @NotNull
  private final LdapUserDBConfig config;
  @NotNull
  private final String baseDn;
  @Nullable
  private final String fakeMailSuffix;

  public LdapUserDB(@NotNull SharedContext context, @NotNull LdapUserDBConfig config) throws Exception {
    URI ldapUri = URI.create(config.getConnectionUrl());
    this.baseDn = ldapUri.getPath().isEmpty() ? "" : ldapUri.getPath().substring(1);
    final ServerSet serverSet = createServerSet(context, config);
    final BindRequest bindRequest = config.getBind().createBindRequest();
    this.pool = new LDAPConnectionPool(serverSet, bindRequest, 1, config.getMaxConnections());
    this.fakeMailSuffix = createFakeMailSuffix(config);
    this.config = config;
  }

  @NotNull
  private static ServerSet createServerSet(@NotNull SharedContext context, @NotNull LdapUserDBConfig config) throws Exception {
    final URI ldapUri = URI.create(config.getConnectionUrl());
    final SocketFactory factory;
    final int defaultPort;
    switch (ldapUri.getScheme().toLowerCase(Locale.ENGLISH)) {
      case "ldap":
        factory = null;
        defaultPort = 389;
        break;
      case "ldaps":
        factory = createSslFactory(context, config);
        defaultPort = 636;
        break;
      default:
        throw new IllegalStateException("Unknown ldap scheme: " + ldapUri.getScheme());
    }
    final String ldapHost = ldapUri.getHost();
    final int ldapPort = ldapUri.getPort() > 0 ? ldapUri.getPort() : defaultPort;
    return new SingleServerSet(ldapHost, ldapPort, factory);
  }

  @Nullable
  private static String createFakeMailSuffix(@NotNull LdapUserDBConfig config) {
    final String suffix = config.getFakeMailSuffix();
    if (suffix.isEmpty()) {
      return null;
    }
    return suffix.indexOf('@') < 0 ? '@' + suffix : suffix;
  }

  @NotNull
  private static SocketFactory createSslFactory(@NotNull SharedContext context, @NotNull LdapUserDBConfig config) throws Exception {
    final String certPem = config.getLdapCertPem();
    if (certPem == null) {
      log.info("LDAP certificate not defined. Using JVM default SSL context");
      return SSLContext.getDefault().getSocketFactory();
    }

    final File certFile = ConfigHelper.joinPath(context.getBasePath(), certPem);

    log.info("Loading LDAP certificate from: {}", certFile);

    final TrustManager[] trustManagers = createTrustManagers(certFile);
    final SSLUtil sslUtil = new SSLUtil(trustManagers);
    return sslUtil.createSSLSocketFactory();
  }

  @NotNull
  private static KeyStore assembleKeyStore(@NotNull X509Certificate certificate) throws Exception {
    final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

    keyStore.load(null);
    keyStore.setCertificateEntry("alias", certificate);

    return keyStore;
  }

  @NotNull
  public static X509Certificate loadCertificate(@NotNull File certFile) throws Exception {
    final X509Certificate certificate;

    final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    try (FileInputStream is = new FileInputStream(certFile)) {
      certificate = (X509Certificate) certificateFactory.generateCertificate(is);
    }

    if (certificate == null)
      throw new IllegalArgumentException(String.format("Failed to read certificate from %s", certFile));

    return certificate;
  }

  @NotNull
  private static TrustManager[] createTrustManagers(@NotNull File certFile) throws Exception {
    final X509Certificate certificate = loadCertificate(certFile);
    final KeyStore keyStore = assembleKeyStore(certificate);
    final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore);
    return factory.getTrustManagers();
  }

  @Override
  public void close() {
    this.pool.close();
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }

  @Override
  public User check(@NotNull String userName, @NotNull String password) throws SVNException {
    return findUser(userName, userDN -> pool.bindAndRevertAuthentication(userDN, password).getResultCode() == ResultCode.SUCCESS);
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String userName) throws SVNException {
    return findUser(userName, userDN -> true);
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) {
    return null;
  }

  private User findUser(@NotNull String userName, @NotNull LdapCheck ldapCheck) throws SVNException {
    try {
      final Filter filter;
      if (!config.getSearchFilter().isEmpty()) {
        filter = Filter.createANDFilter(
            Filter.create(config.getSearchFilter()),
            Filter.createEqualityFilter(config.getLoginAttribute(), userName)
        );
      } else {
        filter = Filter.createEqualityFilter(config.getLoginAttribute(), userName);
      }
      final SearchResult search = pool.search(baseDn, SearchScope.SUB, filter, config.getLoginAttribute(), config.getNameAttribute(), config.getEmailAttribute());
      if (search.getEntryCount() == 1) {
        final SearchResultEntry entry = search.getSearchEntries().get(0);
        final String login = getAttribute(entry, config.getLoginAttribute());
        if (login == null) {
          throw new IllegalStateException("Can't get login for user: " + userName);
        }
        if (ldapCheck.check(entry.getDN())) {
          final String realName = getAttribute(entry, config.getNameAttribute());
          String email = getAttribute(entry, config.getEmailAttribute());
          if (email == null && fakeMailSuffix != null) {
            email = login + fakeMailSuffix;
          }
          return User.create(login, realName != null ? realName : login, email, null);
        }
      }
      return null;
    } catch (LDAPException e) {
      if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
        return null;
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, e.getMessage()), e);
    }
  }

  @Nullable
  private String getAttribute(@NotNull SearchResultEntry entry, @NotNull String name) {
    Attribute attribute = entry.getAttribute(name);
    return attribute == null ? null : attribute.getValue();
  }

  @FunctionalInterface
  private interface LdapCheck {
    boolean check(@NotNull String userDN) throws LDAPException;
  }
}
