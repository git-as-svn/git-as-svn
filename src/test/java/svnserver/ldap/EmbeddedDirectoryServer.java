/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldif.LDIFReader;
import org.jetbrains.annotations.NotNull;
import svnserver.auth.ldap.config.LdapBind;
import svnserver.auth.ldap.config.LdapBindPLAIN;
import svnserver.auth.ldap.config.LdapUserDBConfig;
import svnserver.config.UserDBConfig;

import java.io.InputStream;
import java.net.URL;

/**
 * Embedded LDAP server.
 *
 * @author Artem V. Navrotskiy (bozaro at buzzsoft.ru)
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class EmbeddedDirectoryServer implements AutoCloseable {

  @NotNull
  static final String ADMIN_USERNAME = "ldapadmin";
  @NotNull
  static final String ADMIN_PASSWORD = "123456789012345678901234567890123456789012345678901234567890";
  @NotNull
  private final InMemoryDirectoryServer server;
  @NotNull
  private final DN baseDn;
  private final DirectoryServerNet serverNet;

  private EmbeddedDirectoryServer(@NotNull String dn, @NotNull URL ldifStream, @NotNull DirectoryServerNet serverNet) throws Exception {
    baseDn = new DN(dn);
    this.serverNet = serverNet;

    final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(dn);

    config.setListenerConfigs(serverNet.getListenerConfig());

    server = new InMemoryDirectoryServer(config);

    try (InputStream in = ldifStream.openStream()) {
      server.importFromLDIF(false, new LDIFReader(in));
    }

    server.startListening();
  }

  @NotNull
  public static EmbeddedDirectoryServer create(@NotNull DirectoryServerNet serverNet) throws Exception {
    return new EmbeddedDirectoryServer("dc=example,dc=com", EmbeddedDirectoryServer.class.getResource("ldap.ldif"), serverNet);
  }

  @Override
  public void close() {
    server.shutDown(true);
  }

  @NotNull UserDBConfig createUserConfig() {
    final String connectionUrl = String.format("%s://localhost:%s/%s", serverNet.getUrlSchema(), server.getListenPort(), baseDn);
    final LdapBind ldapBind = new LdapBindPLAIN("u:" + ADMIN_USERNAME, ADMIN_PASSWORD);

    return new LdapUserDBConfig(
        connectionUrl,
        ldapBind,
        "",
        "uid",
        "givenName",
        "mail",
        serverNet.getCertificatePath(),
        3,
        ""
    );
  }
}
