/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap;

import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.util.ssl.SSLUtil;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import svnserver.auth.ldap.LdapUserDB;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class SslDirectoryServerNet implements DirectoryServerNet {

  @NotNull
  private final InMemoryListenerConfig listenerConfig;
  @NotNull
  private final Path certFile;

  SslDirectoryServerNet(@NotNull Path certFile, @NotNull Path keyFile) throws Exception {
    this.certFile = certFile;
    final KeyManager[] keyManagers = createKeyManagers(certFile, keyFile);
    final SSLUtil sslUtil = new SSLUtil(keyManagers, null);
    listenerConfig = InMemoryListenerConfig.createLDAPSConfig(getUrlSchema(), sslUtil.createSSLServerSocketFactory());
  }

  @NotNull
  private static KeyManager[] createKeyManagers(@NotNull Path certFile, @NotNull Path keyFile) throws Exception {
    final X509Certificate certificate = LdapUserDB.loadCertificate(certFile);

    final PrivateKey key = loadKey(keyFile);

    final char[] keyPassword = "12345".toCharArray();
    final KeyStore keyStore = assembleKeyStore(certificate, key, keyPassword);

    final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, keyPassword);

    return keyManagerFactory.getKeyManagers();
  }

  @NotNull
  private static PrivateKey loadKey(@NotNull Path keyFile) throws Exception {
    final PEMKeyPair keyPair;

    try (InputStream keyStream = Files.newInputStream(keyFile);
         Reader keyReader = new InputStreamReader(keyStream, StandardCharsets.US_ASCII);
         PEMParser parser = new PEMParser(keyReader)) {
      keyPair = (PEMKeyPair) parser.readObject();
    }

    Assert.assertNotNull(keyPair);

    final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyPair.getPrivateKeyInfo().getEncoded());
    return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
  }

  @NotNull
  private static KeyStore assembleKeyStore(@NotNull Certificate certificate, @NotNull PrivateKey key, @NotNull char[] keyPassword) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null);

    keyStore.setKeyEntry("key", key, keyPassword, new Certificate[]{certificate});
    return keyStore;
  }

  @Override
  public @NotNull InMemoryListenerConfig getListenerConfig() {
    return listenerConfig;
  }

  @NotNull
  @Override
  public String getUrlSchema() {
    return "ldaps";
  }

  @NotNull
  @Override
  public Path getCertificatePath() {
    return certFile;
  }

  @Override
  public String toString() {
    return getUrlSchema();
  }
}
