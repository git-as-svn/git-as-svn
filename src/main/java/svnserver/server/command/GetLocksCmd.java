/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.Depth;
import svnserver.repository.locks.LockDesc;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.Iterator;

/**
 * <pre>
 * get-locks
 *    params:    ( path:string ? [ depth:word ] )
 *    response   ( ( lock:lockdesc ... ) )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GetLocksCmd extends BaseCmd<GetLocksCmd.Params> {

  public static final class Params {
    @NotNull
    private final String path;
    @NotNull
    private final Depth depth;

    public Params(@NotNull String path, @NotNull String[] depth) {
      this.path = path;
      this.depth = depth.length == 0 ? Depth.Infinity : Depth.parse(depth[0]);
    }
  }

  @NotNull
  @Override
  public Class<? extends Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final String path = context.getRepositoryPath(args.path);
    final SvnServerWriter writer = context.getWriter();
    final Iterator<LockDesc> locks = context.getRepository().wrapLockRead((lockManager) -> lockManager.getLocks(context.getRepositoryPath(path), args.depth));
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin();

    while (locks.hasNext())
      LockCmd.writeLock(writer, locks.next());

    writer
        .listEnd()
        .listEnd()
        .listEnd();
  }
}
