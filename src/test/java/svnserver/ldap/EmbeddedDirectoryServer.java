package svnserver.ldap;

import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.handlers.bind.digestMD5.DigestMd5MechanismHandler;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.jetbrains.annotations.NotNull;
import svnserver.config.LDAPUserDBConfig;
import svnserver.config.UserDBConfig;
import svnserver.parser.TestHelper;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;

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

  private EmbeddedDirectoryServer(@NotNull String dn, @NotNull URL ldifStream) throws Exception {
    // Initialize the LDAP service
    service = new DefaultDirectoryService();
    service.setWorkingDirectory(TestHelper.createTempDir("ldap"));

    // Disable the ChangeLog system
    service.getChangeLog().setEnabled(false);

    // Create a new partition named 'apache'.
    Partition apachePartition = addPartition(new LdapDN(dn));

    // Index some attributes on the apache partition
    addIndex(apachePartition, "objectClass", "ou", "uid");

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

  /**
   * Add a new partition to the server
   *
   * @param partitionDn The partition DN
   * @return The newly added partition
   * @throws Exception If the partition can't be added
   */
  private Partition addPartition(@NotNull LdapDN partitionDn) throws Exception {
    // Create a new partition named 'foo'.
    Partition partition = new JdbmPartition();
    partition.setId(partitionDn.getRdn().getNormValue());
    partition.setSuffix(partitionDn.getNormName());
    service.addPartition(partition);
    return partition;
  }

  /**
   * Add a new set of index on the given attributes
   *
   * @param partition The partition on which we want to add index
   * @param attrs     The list of attributes to index
   */
  private void addIndex(Partition partition, String... attrs) {
    // Index some attributes on the apache partition
    HashSet<Index<?, ServerEntry>> indexedAttributes = new HashSet<>();
    for (String attribute : attrs) {
      indexedAttributes.add(new JdbmIndex<String, ServerEntry>(attribute));
    }
    ((JdbmPartition) partition).setIndexedAttributes(indexedAttributes);
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
    final LDAPUserDBConfig config = new LDAPUserDBConfig();
    config.setConnectionUrl("ldap://" + ldapServer.getSaslHost() + ":" + ldapServer.getPort() + "/" + service.getPartitions().iterator().next().getSuffixDn().getUpName());
    config.setUserSearch("(uid={0})");
    config.setUserSubtree(true);
    config.setNameAttribute("givenName");
    return config;
  }
}
