/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapdb.DB;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.repository.git.GitObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Get object as is.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitFilterRaw implements GitFilter {
  @NotNull
  private final DB cacheDb;
  @NotNull
  public static final String NAME = "raw";

  public GitFilterRaw(@NotNull LocalContext context) {
    this.cacheDb = context.getShared().getCacheDB();
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public String getMd5(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    return GitFilterHelper.getMd5(this, cacheDb, objectId, false);
  }

  @Override
  public long getSize(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    final ObjectReader reader = objectId.getRepo().newObjectReader();
    return reader.getObjectSize(objectId.getObject(), Constants.OBJ_BLOB);
  }

  @NotNull
  @Override
  public InputStream inputStream(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    final ObjectLoader loader = objectId.openObject();
    return loader.openStream();
  }

  @NotNull
  @Override
  public OutputStream outputStream(@NotNull OutputStream stream, @Nullable User user) {
    return stream;
  }
}
