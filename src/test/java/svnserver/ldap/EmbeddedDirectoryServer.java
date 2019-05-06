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
import svnserver.auth.ldap.config.LdapBindSimple;
import svnserver.auth.ldap.config.LdapUserDBConfig;
import svnserver.config.UserDBConfig;

import java.io.InputStream;
import java.net.URL;

/**
 * Embedded LDAP server.
 *
 * @author Artem V. Navrotskiy (bozaro at buzzsoft.ru)
 */
public final class EmbeddedDirectoryServer implements AutoCloseable {

  @NotNull
  static final String ADMIN_PASSWORD = "123456789012345678901234567890123456789012345678901234567890";
  @NotNull
  private final InMemoryDirectoryServer server;
  @NotNull
  private final DN baseDn;

  private EmbeddedDirectoryServer(@NotNull String dn, @NotNull URL ldifStream) throws Exception {
    baseDn = new DN(dn);

    final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(dn);
    server = new InMemoryDirectoryServer(config);

    try (InputStream in = ldifStream.openStream()) {
      server.importFromLDIF(false, new LDIFReader(in));
    }

    server.startListening();
  }

  @NotNull
  public static EmbeddedDirectoryServer create() throws Exception {
    return new EmbeddedDirectoryServer("dc=example,dc=com", EmbeddedDirectoryServer.class.getResource("ldap.ldif"));
  }

  @Override
  public void close() {
    server.shutDown(true);
  }

  @NotNull UserDBConfig createUserConfig() {
    final LdapUserDBConfig config = new LdapUserDBConfig();
    config.setBind(new LdapBindSimple(LdapBindSimple.BindType.Plain, "u:ldapadmin", ADMIN_PASSWORD));
    config.setConnectionUrl("ldap://127.0.0.1:" + server.getListenPort() + "/" + baseDn);
    config.setSearchFilter("");
    config.setLoginAttribute("uid");
    config.setEmailAttribute("mail");
    config.setNameAttribute("givenName");
    config.setMaxConnections(3);
    return config;
  }
}
