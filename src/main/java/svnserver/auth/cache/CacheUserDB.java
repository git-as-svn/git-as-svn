/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache;

import com.google.common.cache.Cache;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.HashHelper;
import svnserver.auth.Authenticator;
import svnserver.auth.PlainAuthenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

/**
 * Caching user authentication result for reduce external API usage.
 *
 * @author Artem V. Navrotskiy
 */
public final class CacheUserDB implements UserDB {
  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));
  @NotNull
  private final static User invalidUser = User.create("invalid", "invalid", null, null);
  @NotNull
  private final UserDB userDB;
  @NotNull
  private final Cache<String, User> cache;

  public CacheUserDB(@NotNull UserDB userDB, @NotNull Cache<String, User> cache) {
    this.userDB = userDB;
    this.cache = cache;
  }

  @Override
  public User check(@NotNull String userName, @NotNull String password) throws SVNException {
    return cached("c." + hash(userName, password), db -> db.check(userName, password));
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String userName) throws SVNException {
    return cached("l." + userName, db -> db.lookupByUserName(userName));
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) throws SVNException {
    return cached("e." + external, db -> db.lookupByExternal(external));
  }

  private User cached(@NotNull String key, @NotNull CachedCallback callback) throws SVNException {
    try {
      final User cachedUser = cache.get(key, () -> {
        final User authUser = callback.exec(userDB);
        return authUser != null ? authUser : invalidUser;
      });
      return cachedUser != invalidUser ? cachedUser : null;
    } catch (ExecutionException e) {
      if (e.getCause() instanceof SVNException) {
        throw (SVNException) e.getCause();
      }
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private String hash(@NotNull String userName, @NotNull String password) {
    final MessageDigest digest = HashHelper.sha256();
    hashPacket(digest, userName.getBytes(StandardCharsets.UTF_8));
    hashPacket(digest, password.getBytes(StandardCharsets.UTF_8));
    return Hex.encodeHexString(digest.digest());
  }

  private void hashPacket(@NotNull MessageDigest digest, @NotNull byte[] packet) {
    int length = packet.length;
    for (; ; ) {
      digest.update((byte) (length & 0xFF));
      if (length == 0) {
        break;
      }
      length = (length >> 8) & 0xFFFFFF;
    }
    digest.update(packet);
  }

  @FunctionalInterface
  private interface CachedCallback {
    @Nullable
    User exec(@NotNull UserDB userDB) throws SVNException;
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }
}
