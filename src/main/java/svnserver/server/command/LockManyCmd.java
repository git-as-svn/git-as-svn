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
import svnserver.repository.locks.LockDesc;
import svnserver.repository.locks.LockTarget;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * <pre>
 * lock-many
 *    params:    ( [ comment:string ] steal-lock:bool ( ( path:string
 *                 [ current-rev:number ] ) ... ) )
 *    Before sending response, server sends lock cmd status and descriptions,
 *    ending with "done".
 *    lock-info: ( success ( lock:lockdesc ) ) | ( failure ( err:error ) )
 *                | done
 *    response: ( )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LockManyCmd extends BaseCmd<LockManyCmd.Params> {

  public static final class PathRev {
    @NotNull
    private final String path;
    @NotNull
    private final int[] rev;

    public PathRev(@NotNull String path, @NotNull int[] rev) {
      this.path = path;
      this.rev = rev;
    }

    @NotNull
    public String getPath() {
      return path;
    }

    @NotNull
    public int[] getRev() {
      return rev;
    }
  }

  public static final class Params {
    @NotNull
    private final String[] comment;
    private final boolean stealLock;
    @NotNull
    private final PathRev[] paths;

    public Params(@NotNull String[] comment, boolean stealLock, @NotNull PathRev[] paths) {
      this.comment = comment;
      this.stealLock = stealLock;
      this.paths = paths;
    }
  }

  @NotNull
  @Override
  public Class<? extends Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final SvnServerWriter writer = context.getWriter();
    final int latestRev = context.getRepository().getLatestRevision().getId();
    final String comment = args.comment.length == 0 ? null : args.comment[0];

    final LockTarget[] targets = new LockTarget[args.paths.length];
    for (int i = 0; i < args.paths.length; ++i) {
      final String path = context.getRepositoryPath(args.paths[i].path);
      final int rev = getRevision(args.paths[i].rev, latestRev);
      targets[i] = new LockTarget(path, rev);
    }

    final LockDesc[] locks;
    try {
      locks = context.getRepository().wrapLockWrite((lockManager) -> lockManager.lock(context, comment, args.stealLock, targets));
      for (LockDesc lock : locks) {
        writer.listBegin().word("success");
        LockCmd.writeLock(writer, lock);
        writer.listEnd();
      }
    } catch (SVNException e) {
      sendError(writer, e.getErrorMessage());
    }

    writer.word("done");
    writer.listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }
}
