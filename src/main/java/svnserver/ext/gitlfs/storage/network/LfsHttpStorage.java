/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.client.Client;
import ru.bozaro.gitlfs.client.auth.AuthProvider;
import ru.bozaro.gitlfs.client.io.StreamProvider;
import ru.bozaro.gitlfs.common.data.Link;
import ru.bozaro.gitlfs.common.data.Links;
import ru.bozaro.gitlfs.common.data.ObjectRes;
import ru.bozaro.gitlfs.common.data.Operation;
import svnserver.TemporaryOutputStream;
import svnserver.auth.User;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.web.client.HttpError;
import svnserver.ext.web.server.WebServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutionException;

/**
 * Http remote storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsHttpStorage implements LfsStorage {
  private static final int MAX_CACHE = 1000;
  @NotNull
  private final URL authUrl;
  @NotNull
  private final String authToken;
  @NotNull
  private final ObjectMapper mapper;
  @NotNull
  private final LoadingCache<User, Link> tokens;

  public LfsHttpStorage(@NotNull URL authUrl, @NotNull String authToken) {
    this.authUrl = authUrl;
    this.authToken = authToken;
    this.mapper = WebServer.createJsonMapper();
    this.tokens = CacheBuilder.newBuilder()
        .maximumSize(MAX_CACHE)
        .build(new CacheLoader<User, Link>() {
          public Link load(@NotNull User user) throws IOException {
            return getTokenUncached(user);
          }
        });
  }

  private void addParameter(@NotNull PostMethod post, @NotNull String key, @Nullable String value) {
    if (value != null) {
      post.addParameter(key, value);
    }
  }

  @NotNull
  private Link getTokenUncached(@NotNull User user) throws IOException {
    final PostMethod post = new PostMethod(authUrl.toString());
    post.addParameter("token", authToken);
    if (!user.isAnonymous()) {
      addParameter(post, "username", user.getUserName());
      addParameter(post, "realname", user.getRealName());
      addParameter(post, "external", user.getExternalId());
      addParameter(post, "email", user.getEmail());
    } else {
      post.addParameter("anonymous", "true");
    }
    final HttpClient client = new HttpClient();
    client.executeMethod(post);
    if (post.getStatusCode() == HttpStatus.SC_OK) {
      return mapper.readValue(post.getResponseBodyAsStream(), Link.class);
    }
    throw new HttpError(post, "Token request failed");
  }

  @NotNull
  private Link getToken(@NotNull User user) throws IOException {
    try {
      return tokens.get(user);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      if (e.getCause() instanceof Error) {
        throw (Error) e.getCause();
      }
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e);
    }
  }

  public void invalidate(@NotNull User user) {
    tokens.invalidate(user);
  }

  @Nullable
  public ObjectRes getMeta(@NotNull String hash) throws IOException {
    final Client lfsClient = new Client(new UserAuthProvider(User.getAnonymous()), new HttpClient());
    return lfsClient.getMeta(hash);
  }

  public boolean putObject(@NotNull User user, StreamProvider streamProvider, String sha, long size) throws IOException {
    final Client lfsClient = new Client(new UserAuthProvider(user), new HttpClient());
    return lfsClient.putObject(streamProvider, sha, size);
  }

  @NotNull
  public InputStream getObject(@NotNull Links links) throws IOException {
    final Client lfsClient = new Client(new UserAuthProvider(User.getAnonymous()), new HttpClient());
    return lfsClient.getObject(null, links, TemporaryOutputStream::new).toInputStream();
  }

  @Nullable
  @Override
  public LfsReader getReader(@NotNull String oid) throws IOException {
    try {
      if (!oid.startsWith(OID_PREFIX)) return null;
      final String hash = oid.substring(OID_PREFIX.length());
      final Client lfsClient = new Client(new UserAuthProvider(User.getAnonymous()), new HttpClient());
      final ObjectRes meta = lfsClient.getMeta(hash);
      if (meta == null || meta.getMeta() == null) {
        return null;
      }
      return new LfsHttpReader(this, meta.getMeta(), meta);
    } catch (HttpError e) {
      e.log();
      throw e;
    }
  }

  @NotNull
  @Override
  public LfsWriter getWriter(@Nullable User user) throws IOException {
    return new LfsHttpWriter(this, user == null ? User.getAnonymous() : user);
  }

  private final class UserAuthProvider implements AuthProvider {
    @NotNull
    private final User user;

    public UserAuthProvider(@NotNull User user) {
      this.user = user;
    }

    @NotNull
    @Override
    public Link getAuth(@NotNull Operation mode) throws IOException {
      return getToken(user);
    }

    @Override
    public void invalidateAuth(@NotNull Operation mode, @NotNull Link auth) {
      tokens.invalidate(user);
    }
  }
}
