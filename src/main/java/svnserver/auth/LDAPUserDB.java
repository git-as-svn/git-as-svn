/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.ConfigHelper;
import svnserver.config.LDAPUserDBConfig;

import javax.naming.NamingException;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
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

  public LDAPUserDB(@NotNull LDAPUserDBConfig config, @NotNull File basePath) {
    URI ldapUri = URI.create(config.getConnectionUrl());
    SocketFactory factory;
    int defaultPort;
    switch (ldapUri.getScheme().toLowerCase()) {
      case "ldap":
        factory = null;
        defaultPort = 389;
        break;
      case "ldaps":
        factory = createSslFactory(config, basePath);
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

  private static SocketFactory createSslFactory(@NotNull LDAPUserDBConfig config, @NotNull File basePath) {
    try {
      final TrustManager trustManager;
      final String certPem = config.getLdapCertPem();
      if (certPem != null) {
        final File certFile = ConfigHelper.joinPath(basePath, certPem);
        log.info("Loading CA certificate from: {}", certFile.getAbsolutePath());
        trustManager = createTrustManager(Files.readAllBytes(certFile.toPath()));
        return new SSLUtil(null, trustManager).createSSLSocketFactory();
      } else {
        log.info("CA certificate not defined. Using JVM default SSL context");
        return SSLContext.getDefault().getSocketFactory();
      }
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new IllegalStateException("Can't load certificate file", e);
    }
  }

  @Nullable
  @Override
  public User check(@NotNull String username, @NotNull String password) throws SVNException {
    try {
      LDAPConnection ldap = new LDAPConnection(socketFactory, ldapHost, ldapPort);
      try {
        ldap.bind(new DIGESTMD5BindRequest(username, password));
        SearchResult search = ldap.search(
            baseDn,
            config.isUserSubtree() ? SearchScope.SUB : SearchScope.ONE,
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
        final SearchResultEntry entry = search.getSearchEntries().get(0);
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
  private String getAttribute(@NotNull SearchResultEntry entry, @NotNull String name) throws NamingException {
    Attribute attribute = entry.getAttribute(name);
    return attribute == null ? null : attribute.getValue();
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }

  @NotNull
  public static byte[] parseDERFromPEM(@NotNull byte[] pem, @NotNull String beginDelimiter, @NotNull String endDelimiter) throws GeneralSecurityException {
    final String data = new String(pem, StandardCharsets.ISO_8859_1);
    String[] tokens = data.split(beginDelimiter);
    if (tokens.length != 2) {
      throw new GeneralSecurityException("Invalid PEM certificate data. Delimiter not found: " + beginDelimiter);
    }
    tokens = tokens[1].split(endDelimiter);
    if (tokens.length != 2) {
      throw new GeneralSecurityException("Invalid PEM certificate data. Delimiter not found: " + endDelimiter);
    }
    return DatatypeConverter.parseBase64Binary(tokens[0]);
  }

  @NotNull
  public static KeyStore getKeyStoreFromDER(@NotNull byte[] certBytes) throws GeneralSecurityException {
    try {
      final CertificateFactory factory = CertificateFactory.getInstance("X.509");
      final KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      keystore.load(null);
      keystore.setCertificateEntry("alias", factory.generateCertificate(new ByteArrayInputStream(certBytes)));
      return keystore;
    } catch (IOException e) {
      throw new KeyStoreException(e);
    }
  }

  @NotNull
  public static TrustManager createTrustManager(@NotNull byte[] pem) throws GeneralSecurityException {
    final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    final KeyStore keystore = getKeyStoreFromDER(parseDERFromPEM(pem, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----"));
    factory.init(keystore);

    final TrustManager[] trustManagers = factory.getTrustManagers();
    return new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        for (TrustManager trustManager : trustManagers) {
          ((X509TrustManager) trustManager).checkClientTrusted(x509Certificates, s);
        }
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        for (TrustManager trustManager : trustManagers) {
          ((X509TrustManager) trustManager).checkServerTrusted(x509Certificates, s);
        }
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };
  }
}
