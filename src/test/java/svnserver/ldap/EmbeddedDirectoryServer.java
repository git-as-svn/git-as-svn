/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap;

import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.constants.SupportedSaslMechanisms;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.schemamanager.impl.DefaultSchemaManager;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.avl.AvlPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.handlers.sasl.digestMD5.DigestMd5MechanismHandler;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.jetbrains.annotations.NotNull;
import svnserver.TestHelper;
import svnserver.auth.ldap.config.LdapBindSimple;
import svnserver.auth.ldap.config.LdapUserDBConfig;
import svnserver.config.UserDBConfig;

import java.io.File;
import java.net.URL;
import java.util.Collections;

/**
 * Embedded LDAP server.
 *
 * @author Artem V. Navrotskiy (bozaro at buzzsoft.ru)
 */
public final class EmbeddedDirectoryServer implements AutoCloseable {
  public static final String HOST = "127.0.0.2";

  @NotNull
  private final DirectoryService service;
  @NotNull
  private final LdapServer ldapServer;
  @NotNull
  private final Dn baseDn;

  private EmbeddedDirectoryServer(@NotNull String dn, @NotNull URL ldifStream) throws Exception {
    // Initialize the LDAP service

    service = new DefaultDirectoryService();
    service.setInstanceLayout(new InstanceLayout(TestHelper.createTempDir("ldap")));

    // Disable the ChangeLog system
    service.getChangeLog().setEnabled(false);

    final SchemaManager schemaManager = new DefaultSchemaManager();
    service.setSchemaManager(schemaManager);

    final SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
    schemaPartition.setWrappedPartition(createPartition(new Dn(SchemaConstants.OU_SCHEMA), schemaManager));
    service.setSchemaPartition(schemaPartition);

    service.setSystemPartition(createPartition(new Dn("ou=system"), schemaManager));

    // Create a new partition
    baseDn = new Dn(dn);
    service.addPartition(createPartition(baseDn, schemaManager));

    ldapServer = new LdapServer();
    ldapServer.setSaslHost(HOST);
    ldapServer.setSearchBaseDn(dn);
    ldapServer.setTransports(new TcpTransport(HOST, 10389));
    ldapServer.addSaslMechanismHandler(SupportedSaslMechanisms.DIGEST_MD5, new DigestMd5MechanismHandler());
    ldapServer.setDirectoryService(service);

    // And start the service
    service.startup();

    final LdifFileLoader ldifLoader = new LdifFileLoader(service.getAdminSession(), new File(ldifStream.toURI()), Collections.emptyList());
    ldifLoader.execute();

    // Bind to port.
    ldapServer.start();
  }

  @NotNull
  private Partition createPartition(@NotNull Dn partitionDn, @NotNull SchemaManager schemaManager) throws Exception {
    // Create a new partition
    AvlPartition partition = new AvlPartition(schemaManager);
    partition.setId(String.valueOf(partitionDn.getRdn().getNormValue().getValue()));
    partition.setSuffixDn(new Dn(partitionDn.getNormName()));
    return partition;
  }

  @Override
  public void close() throws Exception {
    ldapServer.stop();
    service.shutdown();
  }

  @NotNull
  public static EmbeddedDirectoryServer create() throws Exception {
    return new EmbeddedDirectoryServer("dc=example,dc=com", EmbeddedDirectoryServer.class.getResource("ldap.ldif"));
  }

  public UserDBConfig createUserConfig() throws Exception {
    final LdapUserDBConfig config = new LdapUserDBConfig();
    config.setBind(new LdapBindSimple("ldapadmin", "ldapadmin"));
    config.setConnectionUrl("ldap://" + ldapServer.getSaslHost() + ":" + ldapServer.getPort() + "/" + baseDn.getName());
    config.setSearchFilter("");
    config.setLoginAttribute("uid");
    config.setEmailAttribute("mail");
    config.setNameAttribute("givenName");
    config.setMaxConnections(3);
    return config;
  }
}
