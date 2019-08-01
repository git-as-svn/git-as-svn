/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import com.google.common.io.CharStreams;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.jgit.util.Holder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapdb.DBMaker;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import ru.bozaro.gitlfs.client.Client;
import ru.bozaro.gitlfs.client.auth.CachedAuthProvider;
import ru.bozaro.gitlfs.client.exceptions.RequestException;
import ru.bozaro.gitlfs.common.JsonHelper;
import ru.bozaro.gitlfs.common.data.Link;
import ru.bozaro.gitlfs.common.data.Operation;
import svnserver.SvnTestHelper;
import svnserver.SvnTestServer;
import svnserver.VcsAccessEveryone;
import svnserver.VcsAccessNoAnonymous;
import svnserver.auth.LocalUserDB;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.config.SharedConfig;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.server.LfsServer;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.gitlfs.storage.memory.LfsMemoryStorage;
import svnserver.ext.web.config.WebServerConfig;
import svnserver.ext.web.server.WebServer;
import svnserver.repository.VcsAccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple test for LfsLocalStorage.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <m.radchenko@corp.mail.ru>
 */
public final class LfsHttpStorageTest {

  @Test
  public void commitToRemoteLfs() throws Exception {
    // Create users
    final LocalUserDB users = new LocalUserDB();
    final User user = users.add(SvnTestServer.USER_NAME, "test", "Test User", "test@example.com");
    Assert.assertNotNull(user);
    // Create shared context
    try (SharedContext sharedContext = SharedContext.create(new File("/tmp"), "realm", DBMaker.memoryDB().make(), Thread::new, Collections.singletonList(new WebServerConfig(0)))) {
      final WebServer webServer = sharedContext.sure(WebServer.class);
      sharedContext.add(LfsServer.class, new LfsServer("t0ken", 0, 0));
      sharedContext.add(UserDB.class, users);
      sharedContext.ready();
      // Create local context
      LocalContext localContext = new LocalContext(sharedContext, "example");

      localContext.add(VcsAccess.class, new VcsAccessEveryone());

      final LfsMemoryStorage backendStorage = new LfsMemoryStorage();
      localContext.add(LfsStorage.class, backendStorage);
      // Register storage
      sharedContext.sure(LfsServer.class).register(localContext, localContext.sure(LfsStorage.class));

      final URI url = webServer.getBaseUrl().resolve("example.git/").resolve(LfsServer.SERVLET_AUTH);

      try (SvnTestServer server = SvnTestServer.createEmpty(null, false, false, new GitAsSvnLfsHttpStorage(url))) {
        final SVNRepository svnRepository = server.openSvnRepository();
        SvnTestHelper.createFile(svnRepository, ".gitattributes", "* -text\n*.txt filter=lfs diff=lfs merge=lfs -text", null);

        SvnTestHelper.createFile(svnRepository, "1.txt", "some text", null);
        SvnTestHelper.checkFileContent(svnRepository, "1.txt", "some text");

        Assert.assertEquals(backendStorage.getFiles().size(), 1);
        final byte[] lfsBytes = backendStorage.getFiles().values().iterator().next();
        Assert.assertNotNull(lfsBytes);
        Assert.assertEquals(new String(lfsBytes, StandardCharsets.UTF_8), "some text");

        final Holder<SVNLock> lockHolder = new Holder<>(null);
        svnRepository.lock(Collections.singletonMap("1.txt", svnRepository.getLatestRevision()), null, false, new ISVNLockHandler() {
          @Override
          public void handleLock(String path, SVNLock lock, SVNErrorMessage error) {
            Assert.assertEquals(path, "/1.txt");
            lockHolder.set(lock);
          }

          @Override
          public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) {
            Assert.fail();
          }
        });
        final SVNLock lock = lockHolder.get();
        Assert.assertNotNull(lock);
        Assert.assertEquals(lock.getPath(), "/1.txt");
        Assert.assertNotNull(lock.getID());
        Assert.assertEquals(lock.getOwner(), SvnTestServer.USER_NAME);
      }
    }
  }

  @Test
  public void server() throws Exception {
    // Create users
    final LocalUserDB users = new LocalUserDB();
    final User user = users.add("test", "test", "Test User", "test@example.com");
    Assert.assertNotNull(user);
    // Create shared context
    try (SharedContext sharedContext = SharedContext.create(new File("/nonexistent"), "realm", DBMaker.memoryDB().make(), Thread::new, Collections.singletonList(new WebServerConfig(0)))) {
      final WebServer webServer = sharedContext.sure(WebServer.class);
      sharedContext.add(LfsServer.class, new LfsServer("t0ken", 0, 0));
      sharedContext.add(UserDB.class, users);
      sharedContext.ready();
      // Create local context
      LocalContext localContext = new LocalContext(sharedContext, "example");
      localContext.add(VcsAccess.class, new VcsAccessNoAnonymous());
      localContext.add(LfsStorage.class, new LfsMemoryStorage());
      // Register storage
      sharedContext.sure(LfsServer.class).register(localContext, localContext.sure(LfsStorage.class));

      final URI url = webServer.getBaseUrl().resolve("example.git/").resolve(LfsServer.SERVLET_AUTH);
      final LfsHttpStorage storage = new GitAsSvnLfsHttpStorage(url);

      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1, user));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(user)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Write new file AGAIN
      try (final LfsWriter writer = storage.getWriter(user)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Read old file.
      final LfsReader reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1, user);
      Assert.assertNotNull(reader);
      Assert.assertNull(reader.getMd5());
      Assert.assertEquals(reader.getSize(), 15);

      try (final InputStream stream = reader.openStream()) {
        Assert.assertEquals(CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!");
      }
    }
  }

  private static final class GitAsSvnLfsHttpStorage extends LfsHttpStorage implements LfsStorageFactory, SharedConfig {
    @NotNull
    private final URI authUrl;

    private GitAsSvnLfsHttpStorage(@NotNull URI authUrl) {
      this.authUrl = authUrl;
    }

    @Override
    public @NotNull LfsStorage createStorage(@NotNull LocalContext context) {
      return this;
    }

    @Override
    protected @NotNull Client lfsClient(@NotNull User user) {
      final HttpClient httpClient = HttpClients.createDefault();

      return new Client(new CachedAuthProvider() {

        @Override
        protected @NotNull Link getAuthUncached(@NotNull Operation operation) throws IOException {
          final HttpPost post = new HttpPost(authUrl);
          final List<NameValuePair> params = new ArrayList<>();
          addParameter(params, "secretToken", "t0ken");

          if (user.isAnonymous()) {
            addParameter(params, "mode", "anonymous");
          } else {
            addParameter(params, "mode", "username");
            addParameter(params, "userId", user.getUserName());
          }

          post.setEntity(new UrlEncodedFormEntity(params));
          try {
            final HttpResponse response = httpClient.execute(post);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
              return JsonHelper.mapper.readValue(response.getEntity().getContent(), Link.class);
            }
            throw new RequestException(post, response);
          } finally {
            post.abort();
          }
        }

        private void addParameter(@NotNull List<NameValuePair> params, @NotNull String key, @Nullable String value) {
          if (value != null) {
            params.add(new BasicNameValuePair(key, value));
          }
        }
      });
    }

    @Override
    public void invalidate(@NotNull User user) {

    }

    @Override
    public void create(@NotNull SharedContext context) {
      context.add(LfsStorageFactory.class, this);
    }

    @Override
    public void close() {
    }
  }

}
