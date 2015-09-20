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
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.auth.User;
import svnserver.ext.gitlfs.server.LfsAbstractResource;
import svnserver.ext.gitlfs.server.data.Auth;
import svnserver.ext.gitlfs.server.data.Link;
import svnserver.ext.gitlfs.server.data.Meta;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.web.client.HttpError;
import svnserver.ext.web.server.WebServer;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
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

  @FunctionalInterface
  public interface Request<T> {
    @Nullable
    T exec(@NotNull Link link) throws IOException;
  }

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
/*
  private Link

  @NotNull

  private String getToken() {
    HttpClient client = new HttpClient();
    PostMethod post = new PostMethod(new URL(lfs, "objects").toString());
    return null;
  }*/

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
      return mapper.readValue(post.getResponseBodyAsStream(), Auth.class);
    }
    throw new HttpError(post, "Token request failed");
  }

  @NotNull
  private Link getToken(@NotNull User user) throws IOException {
    try {
      return tokens.get(User.getAnonymous());
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

  @Nullable
  public Meta getMeta(@NotNull Link link, @NotNull String hash) throws IOException {
    final GetMethod get = new GetMethod(link.getHref().toString() + "/objects/" + hash);
    get.addRequestHeader(HttpHeaders.ACCEPT, LfsAbstractResource.MIME_TYPE);
    link.addRequestHeader(get);

    final HttpClient client = new HttpClient();
    client.executeMethod(get);
    if (get.getStatusCode() == HttpStatus.SC_OK) {
      return mapper.readValue(get.getResponseBodyAsStream(), Meta.class);
    }
    throw new HttpError(get, "Metadata request failed for object: " + hash);
  }

  @Nullable
  public Meta postMeta(@NotNull Link link, @NotNull String hash, long size) throws IOException {
    final PostMethod put = new PostMethod(link.getHref().toString() + "/objects");
    put.addRequestHeader(HttpHeaders.ACCEPT, LfsAbstractResource.MIME_TYPE);
    link.addRequestHeader(put);
    final byte[] content = mapper.writeValueAsBytes(new Meta(hash, size, Collections.emptyMap()));
    put.setRequestEntity(new ByteArrayRequestEntity(content, LfsAbstractResource.MIME_TYPE));

    final HttpClient client = new HttpClient();
    client.executeMethod(put);
    if (put.getStatusCode() == HttpStatus.SC_OK) {
      return mapper.readValue(put.getResponseBodyAsStream(), Meta.class);
    }
    if (put.getStatusCode() == HttpStatus.SC_ACCEPTED) {
      return mapper.readValue(put.getResponseBodyAsStream(), Meta.class);
    }
    throw new HttpError(put, "Metadata request failed for object: " + hash);
  }

  public <T> T makeRequest(@NotNull User user, @NotNull Request<T> request) throws IOException {
    for (int pass = 0; ; pass++) {
      final Link link = getToken(user);
      try {
        return request.exec(link);
      } catch (HttpError e) {
        if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
          return null;
        }
        if (e.getStatusCode() == HttpStatus.SC_UNAUTHORIZED && pass == 0) {
          if (tokens.getIfPresent(user) == link)
            tokens.invalidate(user);
          continue;
        }
        throw e;
      }
    }
  }

  @Nullable
  @Override
  public LfsReader getReader(@NotNull String oid) throws IOException {
    try {
      if (!oid.startsWith(OID_PREFIX)) return null;
      final String hash = oid.substring(OID_PREFIX.length());
      final Meta meta = makeRequest(User.getAnonymous(), (link) -> getMeta(link, hash));
      if (meta == null) {
        return null;
      }
      throw new NoSuchMethodError();
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
}
