/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.jetbrains.annotations.NotNull;
import svnserver.context.Local;
import svnserver.ext.gitlfs.storage.LfsStorage;

/**
 * LFS server entry point.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServerEntry implements Local {
  @NotNull
  private final LfsServer server;
  @NotNull
  private final String name;

  public LfsServerEntry(@NotNull LfsServer server, @NotNull String name, @NotNull LfsStorage storage) {
    this.server = server;
    this.name = name;
    server.register(name, storage);
  }

  @Override
  public void close() throws Exception {
    server.unregister(name);
  }
}
