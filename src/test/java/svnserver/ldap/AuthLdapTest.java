/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;
import svnserver.auth.User;
import svnserver.auth.UserDB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

  @Test
  public void validUserPooled() throws Throwable {
    try (
        EmbeddedDirectoryServer ldap = EmbeddedDirectoryServer.create();
        SvnTestServer server = SvnTestServer.createEmpty(ldap.createUserConfig())
    ) {
      final ExecutorService pool = Executors.newFixedThreadPool(10);
      final AtomicBoolean done = new AtomicBoolean(false);
      final UserDB userDB = server.getContext().sure(UserDB.class);

      final List<Callable<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < 1000; ++i) {
        tasks.add(new SuccessAuth(userDB, done, "ldapadmin", "ldapadmin"));
        tasks.add(new SuccessAuth(userDB, done, "simple", "simple"));
        tasks.add(new InvalidAuth(userDB, done, "simple", "hacker"));
      }
      try {
        for (Future<?> future : pool.invokeAll(tasks)) {
          Assert.assertFalse(done.get());
          future.get(300, TimeUnit.SECONDS);
        }
      } finally {
        done.set(true);
        pool.shutdown();
      }
    }
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
      repo.setAuthenticationManager(BasicAuthenticationManager.newInstance(login, password.toCharArray()));
      repo.getLatestRevision();
    }
  }

  private static class SuccessAuth implements Callable<Void> {
    @NotNull
    private final AtomicBoolean done;
    @NotNull
    private final UserDB userDB;
    @NotNull
    private final String username;
    @NotNull
    private final String password;

    public SuccessAuth(@NotNull UserDB userDB, @NotNull AtomicBoolean done, @NotNull String username, @NotNull String password) {
      this.done = done;
      this.userDB = userDB;
      this.username = username;
      this.password = password;
    }

    @Override
    public Void call() throws Exception {
      if (done.get()) return null;
      try {
        final User user = userDB.check(username, password);
        Assert.assertNotNull(user);
        Assert.assertEquals(user.getUserName(), username);
      } catch (IOException | SVNException e) {
        done.set(false);
      }
      return null;
    }
  }

  private static class InvalidAuth implements Callable<Void> {
    @NotNull
    private final AtomicBoolean done;
    @NotNull
    private final UserDB userDB;
    @NotNull
    private final String username;
    @NotNull
    private final String password;

    public InvalidAuth(@NotNull UserDB userDB, @NotNull AtomicBoolean done, @NotNull String username, @NotNull String password) {
      this.done = done;
      this.userDB = userDB;
      this.username = username;
      this.password = password;
    }

    @Override
    public Void call() throws Exception {
      if (done.get()) return null;
      try {
        final User user = userDB.check(username, password);
        Assert.assertNull(user);
      } catch (IOException | SVNException e) {
        done.set(false);
      }
      return null;
    }
  }
}
