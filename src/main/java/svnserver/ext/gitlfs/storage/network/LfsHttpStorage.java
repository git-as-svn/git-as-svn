/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import com.beust.jcommander.internal.Lists;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bozaro.gitlfs.client.Client;
import ru.bozaro.gitlfs.client.auth.AuthProvider;
import ru.bozaro.gitlfs.client.exceptions.RequestException;
import ru.bozaro.gitlfs.client.io.StreamProvider;
import ru.bozaro.gitlfs.common.data.*;
import svnserver.TemporaryOutputStream;
import svnserver.auth.User;
import svnserver.ext.gitlfs.config.LfsConfig;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.web.server.WebServer;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.io.InputStream;
import java.lang.Error;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * HTTP remote storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsHttpStorage implements LfsStorage {
  private static final int MAX_CACHE = 1000;
  private static final String LFS_PATH = ".git/info/lfs";
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LfsHttpStorage.class);
  private URL authUrl;
  @NotNull
  private final ObjectMapper mapper;
  @NotNull
  private final HttpClient httpClient = HttpClients.createDefault();
  private String authToken;
  private LfsConfig config;
  private LoadingCache<User, Link> tokens;

  public LfsHttpStorage(@NotNull URL authUrl, @NotNull String authToken) {
    this(null);
    this.authUrl = authUrl;
    this.authToken = authToken;
    this.tokens = CacheBuilder.newBuilder()
        .maximumSize(MAX_CACHE)
        .build(new CacheLoader<User, Link>() {
          public Link load(@NotNull User user) throws IOException {
            return getTokenUncached(user);
          }
        });
  }

  public LfsHttpStorage(LfsConfig config) {
    this.config = config;
    this.mapper = WebServer.createJsonMapper();
  }

  @NotNull
  private Link getTokenUncached(@NotNull User user) throws IOException {
    final HttpPost post = new HttpPost(authUrl.toString());
    final List<NameValuePair> params = new ArrayList<>();
    addParameter(params, "secretToken", authToken);
    if (user.getExternalId() != null) {
      addParameter(params, "userId", user.getExternalId());
      addParameter(params, "mode", "external");
    } else if (user.isAnonymous()) {
      addParameter(params, "mode", "anonymous");
    } else {
      addParameter(params, "userId", user.getUserName());
      addParameter(params, "mode", "username");
    }
    post.setEntity(new UrlEncodedFormEntity(params));
    try {
      final HttpResponse response = httpClient.execute(post);
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return mapper.readValue(response.getEntity().getContent(), Link.class);
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
    if (tokens != null)
      tokens.invalidate(user);
  }

  @Nullable
  public ObjectRes getMeta(@NotNull String hash) throws IOException {
    final Client lfsClient = lfsClient(User.getAnonymous());
    return lfsClient.getMeta(hash);
  }

  protected AuthProvider getAuthProvider(User user) {
    return new UserAuthProvider(user);
  }

  public boolean putObject(@NotNull User user, StreamProvider streamProvider, String sha, long size) throws IOException {
    final Client lfsClient = lfsClient(user);
    return lfsClient.putObject(streamProvider, sha, size);
  }

  @NotNull
  public InputStream getObject(@NotNull Links links) throws IOException {
    final Client lfsClient = lfsClient();
    return lfsClient.getObject(null, links, TemporaryOutputStream::new).toInputStream();
  }

  private Client lfsClient() {
    return lfsClient(null);
  }

  private Client lfsClient(User user) {
    return new Client(
        getAuthProvider(user == null ? SessionContext.get().getUser() : user),
        httpClient
    );
  }

  @Nullable
  public LfsReader getReader(@NotNull String oid) throws IOException {
    try {
      if (!oid.startsWith(OID_PREFIX)) return null;
      final String hash = oid.substring(OID_PREFIX.length());
      final Client lfsClient = lfsClient();
      BatchRes res = lfsClient.postBatch(new BatchReq(Operation.Download, Lists.newArrayList(new Meta(hash, -1))));
      if (res.getObjects().isEmpty())
        return null;
      BatchItem item = res.getObjects().get(0);
      final ObjectRes meta = new ObjectRes(item.getOid(), item.getSize(), item.getLinks());
      return new LfsHttpReader(this, meta.getMeta(), meta);
    } catch (RequestException e) {
      log.error("HTTP request error:" + e.getMessage() + "\n" + e.getRequestInfo());
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

    private URI uri() {
      URI href = URI.create(SessionContext.get().getRepositoryInfo().getBaseUrl().toDecodedString());
      try {
        return new URI(
            config.getScheme(),
            href.getAuthority(),
            href.getPath() + LFS_PATH,
            null, null
        );
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    @NotNull
    @Override
    public Link getAuth(@NotNull Operation mode) throws IOException {
      if (tokens != null) {
        return getToken(user);
      }
      return new Link(
          uri(),
          ImmutableMap.of(config.getTokenHeader(), nullToEmpty(config.getTokenPrefix()) + user.getToken()),
          null
      );
    }

    @Override
    public void invalidateAuth(@NotNull Operation mode, @NotNull Link auth) {
      if (tokens != null)
        tokens.invalidate(user);
    }
  }
}
