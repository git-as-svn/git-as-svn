/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.tester;

import org.apache.commons.lang.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import svnserver.TestHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

/**
 * Listener for creating SvnTesterExternal.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnTesterExternalListener implements ITestListener {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SvnTesterExternalListener.class);
  @NotNull
  private static final String USER_NAME = "tester";
  @NotNull
  private static final String PASSWORD = "passw0rd";
  @NotNull
  private static final String HOST = "127.0.0.2";
  @NotNull
  private static final String CONFIG_SERVER = "" +
      "[general]\n" +
      "anon-access = none\n" +
      "auth-access = write\n" +
      "password-db = {0}\n";
  @NotNull
  private static final String CONFIG_PASSWD = "" +
      "[users]\n" +
      "{0} = {1}\n";
  private static final long SERVER_STARTUP_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
  private static final long SERVER_STARTUP_DELAY = TimeUnit.MILLISECONDS.toMillis(20);
  @Nullable
  private static NativeDaemon daemon;

  @Override
  public void onTestStart(ITestResult result) {
  }

  @Override
  public void onTestSuccess(ITestResult result) {
  }

  @Override
  public void onTestFailure(ITestResult result) {
  }

  @Override
  public void onTestSkipped(ITestResult result) {
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
  }

  @Override
  public void onStart(ITestContext context) {
    try {
      if (System.getenv("TRAVIS") != null) {
        log.warn("Native svn daemon disabled on travis");
        return;
      }
      final String svnserve = findExecutable("svnserve");
      final String svnadmin = findExecutable("svnadmin");
      if (svnserve != null && svnadmin != null) {
        log.warn("Native svn daemon executables: {}, {}", svnserve, svnadmin);
        daemon = new NativeDaemon(svnserve, svnadmin);
      } else {
        log.warn("Native svn daemon disabled");
      }

      //SvnTesterExternal.create();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Nullable
  private static String findExecutable(@NotNull String name) {
    final String path = System.getenv("PATH");
    if (path != null) {
      final String suffix = SystemUtils.IS_OS_WINDOWS ? ".exe" : "";
      for (String dir : path.split(File.pathSeparator)) {
        final File file = new File(dir, name + suffix);
        if (file.exists()) {
          return file.getAbsolutePath();
        }
      }
    }
    return null;
  }

  @Override
  public void onFinish(ITestContext context) {
    if (daemon != null) {
      try {
        daemon.close();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      } finally {
        daemon = null;
      }
    }
  }

  @Nullable
  public static SvnTesterFactory get() {
    return daemon;
  }

  private static class NativeDaemon implements SvnTesterFactory, AutoCloseable {
    @NotNull
    private final Process daemon;
    @NotNull
    private final File repo;
    @NotNull
    private final SVNURL url;

    public NativeDaemon(@NotNull String svnserve, @NotNull String svnadmin) throws IOException, InterruptedException, SVNException {
      int port = detectPort();
      url = SVNURL.create("svn", null, HOST, port, null, true);
      repo = TestHelper.createTempDir("git-as-svn-repo");
      log.info("Starting native svn daemon at: {}, url: {}", repo, url);
      Runtime.getRuntime().exec(new String[]{
          svnadmin,
          "create",
          repo.getAbsolutePath()
      }).waitFor();
      File config = createConfigs(repo);
      daemon = Runtime.getRuntime().exec(new String[]{
          svnserve,
          "--daemon",
          "--root", repo.getAbsolutePath(),
          "--config-file", config.getAbsolutePath(),
          "--listen-host", HOST,
          "--listen-port", Integer.toString(port)
      });
      long serverStartupTimeout = System.currentTimeMillis() + SERVER_STARTUP_TIMEOUT;
      while (true) {
        try {
          SVNRepositoryFactory.create(url).getRevisionPropertyValue(0, "example");
        } catch (SVNAuthenticationException ignored) {
          break;
        } catch (SVNException e) {
          if ((e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_IO_ERROR) && (System.currentTimeMillis() < serverStartupTimeout)) {
            Thread.sleep(SERVER_STARTUP_DELAY);
            continue;
          }
          throw e;
        }
        break;
      }
    }

    @NotNull
    private static File createConfigs(@NotNull File repo) throws IOException {
      final File config = new File(repo, "conf/server.conf");
      final File passwd = new File(repo, "conf/server.passwd");
      try (Writer writer = new FileWriter(config)) {
        writer.write(MessageFormat.format(CONFIG_SERVER, passwd.getAbsolutePath()));
      }
      try (Writer writer = new FileWriter(passwd)) {
        writer.write(MessageFormat.format(CONFIG_PASSWD, USER_NAME, PASSWORD));
      }
      return config;
    }

    private int detectPort() throws IOException {
      try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName(HOST))) {
        return socket.getLocalPort();
      }
    }

    @NotNull
    @Override
    public SvnTester create() throws Exception {
      return new SvnTesterExternal(url, new BasicAuthenticationManager(USER_NAME, PASSWORD));
    }

    @Override
    public void close() throws Exception {
      log.info("Stopping native svn daemon.");
      daemon.destroy();
      daemon.waitFor();
      TestHelper.deleteDirectory(repo);
    }
  }
}
