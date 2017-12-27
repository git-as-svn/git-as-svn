/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter;

import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.repository.git.GitObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip filter. Useful for testing.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFilterGzip implements GitFilter {
  @NotNull
  private final Map<String, String> cacheMd5;
  @NotNull
  private final Map<String, Long> cacheSize;

  public GitFilterGzip(@NotNull LocalContext context) {
    this.cacheMd5 = GitFilterHelper.getCacheMd5(this, context.getShared().getCacheDB());
    this.cacheSize = GitFilterHelper.getCacheSize(this, context.getShared().getCacheDB());
  }

  @NotNull
  @Override
  public String getName() {
    return "gzip";
  }

  @NotNull
  @Override
  public String getMd5(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    return GitFilterHelper.getMd5(this, cacheMd5, cacheSize, objectId);
  }

  @Override
  public long getSize(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    return GitFilterHelper.getSize(this, cacheMd5, cacheSize, objectId);
  }

  @NotNull
  @Override
  public InputStream inputStream(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    return new GZIPInputStream(objectId.openObject().openStream());
  }

  @NotNull
  @Override
  public OutputStream outputStream(@NotNull OutputStream stream, @Nullable User user) throws IOException {
    return new GZIPOutputStream(stream);
  }
}
