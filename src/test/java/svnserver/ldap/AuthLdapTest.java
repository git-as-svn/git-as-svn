package svnserver.ldap;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import svnserver.SvnTestServer;

/**
 * LDAP authentication test.
 *
 * @author Artem V. Navrotskiy (bozaro at buzzsoft.ru)
 */
public class AuthLdapTest {
  @Test
  public void validUser() throws Throwable {
    checkUser("ldapadmin", "ldapadmin");
  }

  @Test(expectedExceptions = SVNAuthenticationException.class)
  public void invalidPassword() throws Throwable {
    checkUser("ldapadmin", "ldapadmin2");
  }

  @Test(expectedExceptions = SVNAuthenticationException.class)
  public void invalidUser() throws Throwable {
    checkUser("ldapadmin2", "ldapadmin");
  }

  private void checkUser(@NotNull String login, @NotNull String password) throws Throwable {
    try (
        EmbeddedDirectoryServer ldap = EmbeddedDirectoryServer.create();
        SvnTestServer server = SvnTestServer.createEmpty(ldap.createUserConfig())
    ) {
      server.openSvnRepository(login, password).getLatestRevision();
    }
  }
}
