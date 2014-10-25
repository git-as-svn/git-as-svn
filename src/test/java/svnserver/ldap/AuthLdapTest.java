/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
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
      final SVNRepository repo = server.openSvnRepository();
      repo.setAuthenticationManager(new BasicAuthenticationManager(login, password));
      repo.getLatestRevision();
    }
  }
}
