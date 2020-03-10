/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import svnserver.SvnTestHelper;
import svnserver.SvnTestServer;
import svnserver.auth.User;
import svnserver.auth.UserDB;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LDAP authentication test.
 *
 * @author Artem V. Navrotskiy (bozaro at buzzsoft.ru)
 */
public final class AuthLdapTest {

  @DataProvider
  public static Object[][] sslModes() throws Exception {
    final Path cert = Paths.get(AuthLdapTest.class.getResource("cert.pem").toURI());
    final Path key = Paths.get(AuthLdapTest.class.getResource("key.pem").toURI());

    return new Object[][]{
        {RawDirectoryServerNet.instance},
        {new SslDirectoryServerNet(cert, key)},
    };
  }

  /**
   * Test for #156, #242.
   */
  @Test(dataProvider = "sslModes")
  void nativeClient(@NotNull DirectoryServerNet serverNet) throws Exception {
    final String svn = SvnTestHelper.findExecutable("svn");
    if (svn == null)
      throw new SkipException("Native svn executable not found");

    try (
        EmbeddedDirectoryServer ldap = EmbeddedDirectoryServer.create(serverNet);
        SvnTestServer server = SvnTestServer.createEmpty(ldap.createUserConfig(), false)
    ) {
      final String[] command = {svn, "--non-interactive", "ls", "--username=" + EmbeddedDirectoryServer.ADMIN_USERNAME, "--password=" + EmbeddedDirectoryServer.ADMIN_PASSWORD, server.getUrl().toString()};
      final int exitCode = new ProcessBuilder(command)
          .redirectError(ProcessBuilder.Redirect.INHERIT)
          .redirectOutput(ProcessBuilder.Redirect.INHERIT)
          .start()
          .waitFor();
      Assert.assertEquals(exitCode, 0);
    }
  }

  @Test(dataProvider = "sslModes")
  public void validUser(@NotNull DirectoryServerNet serverNet) throws Throwable {
    checkUser(EmbeddedDirectoryServer.ADMIN_USERNAME, EmbeddedDirectoryServer.ADMIN_PASSWORD, serverNet);
  }

  private void checkUser(@NotNull String login, @NotNull String password, @NotNull DirectoryServerNet serverNet) throws Exception {
    try (
        EmbeddedDirectoryServer ldap = EmbeddedDirectoryServer.create(serverNet);
        SvnTestServer server = SvnTestServer.createEmpty(ldap.createUserConfig(), false)
    ) {
      server.openSvnRepository(login, password).getLatestRevision();
    }
  }

  @Test
  public void validUserPooled() throws Throwable {
    try (
        EmbeddedDirectoryServer ldap = EmbeddedDirectoryServer.create(RawDirectoryServerNet.instance);
        SvnTestServer server = SvnTestServer.createEmpty(ldap.createUserConfig(), false)
    ) {
      final ExecutorService pool = Executors.newFixedThreadPool(10);
      final AtomicBoolean done = new AtomicBoolean(false);
      final UserDB userDB = server.getContext().sure(UserDB.class);

      final List<Callable<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < 1000; ++i) {
        tasks.add(new SuccessAuth(userDB, done, EmbeddedDirectoryServer.ADMIN_USERNAME, EmbeddedDirectoryServer.ADMIN_PASSWORD));
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

  @Test(dataProvider = "sslModes")
  public void invalidPassword(@NotNull DirectoryServerNet serverNet) {
    Assert.expectThrows(SVNAuthenticationException.class, () -> checkUser(EmbeddedDirectoryServer.ADMIN_USERNAME, "wrongpassword", serverNet));
  }

  @Test(dataProvider = "sslModes")
  public void invalidUser(@NotNull DirectoryServerNet serverNet) {
    Assert.expectThrows(SVNAuthenticationException.class, () -> checkUser("ldapadmin2", EmbeddedDirectoryServer.ADMIN_PASSWORD, serverNet));
  }

  @Test(dataProvider = "sslModes")
  public void anonymousUserAllowed(@NotNull DirectoryServerNet serverNet) throws Throwable {
    checkAnonymous(true, serverNet);
  }

  private void checkAnonymous(boolean anonymousRead, @NotNull DirectoryServerNet serverNet) throws Exception {
    try (
        EmbeddedDirectoryServer ldap = EmbeddedDirectoryServer.create(serverNet);
        SvnTestServer server = SvnTestServer.createEmpty(ldap.createUserConfig(), anonymousRead)
    ) {
      server.openSvnRepository().getLatestRevision();
    }
  }

  @Test(dataProvider = "sslModes")
  public void anonymousUserDenies(@NotNull DirectoryServerNet serverNet) {
    Assert.expectThrows(SVNAuthenticationException.class, () -> checkAnonymous(false, serverNet));
  }

  private static final class SuccessAuth implements Callable<Void> {
    @NotNull
    private final AtomicBoolean done;
    @NotNull
    private final UserDB userDB;
    @NotNull
    private final String username;
    @NotNull
    private final String password;

    private SuccessAuth(@NotNull UserDB userDB, @NotNull AtomicBoolean done, @NotNull String username, @NotNull String password) {
      this.done = done;
      this.userDB = userDB;
      this.username = username;
      this.password = password;
    }

    @Override
    public Void call() {
      if (done.get()) return null;
      try {
        final User user = userDB.check(username, password);
        Assert.assertNotNull(user);
        Assert.assertEquals(user.getUsername(), username);
      } catch (SVNException e) {
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

    private InvalidAuth(@NotNull UserDB userDB, @NotNull AtomicBoolean done, @NotNull String username, @NotNull String password) {
      this.done = done;
      this.userDB = userDB;
      this.username = username;
      this.password = password;
    }

    @Override
    public Void call() {
      if (done.get()) return null;
      try {
        final User user = userDB.check(username, password);
        Assert.assertNull(user);
      } catch (SVNException e) {
        done.set(false);
      }
      return null;
    }
  }
}
