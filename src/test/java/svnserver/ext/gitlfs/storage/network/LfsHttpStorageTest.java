/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import com.google.common.io.CharStreams;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapdb.DBMaker;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNException;
import svnserver.TestHelper;
import svnserver.auth.LocalUserDB;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.auth.UserWithPassword;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.server.LfsServer;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.gitlfs.storage.local.LfsLocalStorage;
import svnserver.ext.gitlfs.storage.memory.LfsMemoryStorage;
import svnserver.ext.web.config.WebServerConfig;
import svnserver.ext.web.server.WebServer;
import svnserver.ext.web.token.EncryptionFactoryAes;
import svnserver.repository.VcsAccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Simple test for LfsLocalStorage.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsHttpStorageTest {
  @DataProvider(name = "compressProvider")
  public static Object[][] compressProvider() {
    return new Object[][]{
        {true},
        {false},
    };
  }

  @Test
  public void server() throws Exception {
    // Create web server
    final ServerConnector http = createJettyServer();
    final Server jetty = http.getServer();
    // Create users
    final LocalUserDB users = new LocalUserDB();
    final User user = User.create("test", "Test User", "test@example.com", null);
    users.add(new UserWithPassword(user, "test"));
    // Create shared context
    SharedContext sharedContext = new SharedContext(new File("/tmp"), DBMaker.newMemoryDB().make());
    sharedContext.add(WebServer.class, new WebServer(sharedContext, jetty, new WebServerConfig(), new EncryptionFactoryAes("secret")));
    sharedContext.add(LfsServer.class, new LfsServer("{0}.git", "t0ken"));
    sharedContext.add(UserDB.class, users);
    sharedContext.ready();
    // Create local context
    LocalContext localContext = new LocalContext(sharedContext, "example");
    localContext.add(VcsAccess.class, new VcsAccess() {
      @Override
      public void checkRead(@NotNull User user, @Nullable String path) throws SVNException, IOException {
      }

      @Override
      public void checkWrite(@NotNull User user, @Nullable String path) throws SVNException, IOException {
      }
    });
    localContext.add(LfsStorage.class, new LfsMemoryStorage());
    // Register storage
    sharedContext.sure(LfsServer.class).register(localContext, localContext.sure(LfsStorage.class));

    try {
      URL url = new URL("http", http.getHost(), http.getLocalPort(), "/");
      final URL authUrl = new URL(url, "example.git/" + LfsServer.SERVLET_AUTH);
      LfsHttpStorage storage = new LfsHttpStorage(authUrl, "t0ken");

      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308"));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(user)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Read old file.
      final LfsReader reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      Assert.assertNotNull(reader);
      Assert.assertNull(reader.getMd5());
      Assert.assertEquals(reader.getSize(), 15);

      try (final InputStream stream = reader.openStream()) {
        Assert.assertEquals(CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!");
      }
    } finally {
      jetty.stop();
    }
  }

  @Test(dataProvider = "compressProvider")
  public void simple(boolean compress) throws IOException {
    final File tempDir = TestHelper.createTempDir("git-as-svn");
    try {
      LfsLocalStorage storage = new LfsLocalStorage(new File(tempDir, "data"), new File(tempDir, "meta"), compress);
      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308"));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(null)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Read old file.
      final LfsReader reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      Assert.assertNotNull(reader);
      Assert.assertEquals("9fe77772b085e3533101d59d33a51f19", reader.getMd5());
      Assert.assertEquals(15, reader.getSize());

      // Write new file
      try (final LfsWriter writer = storage.getWriter(null)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      try (final InputStream stream = reader.openStream()) {
        Assert.assertEquals(CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!");
      }
    } finally {
      TestHelper.deleteDirectory(tempDir);
    }
  }

  @NotNull
  private ServerConnector createJettyServer() {
    final Server server = new Server();
    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory());
    http.setPort(0);
    http.setHost("127.0.1.1");
    http.setIdleTimeout(30000);
    server.addConnector(http);
    return http;
  }
}
