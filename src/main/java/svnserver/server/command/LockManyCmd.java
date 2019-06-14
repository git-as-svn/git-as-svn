/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.common.LockConflictException;
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

  @NotNull
  @Override
  public Class<? extends Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException {
    final SvnServerWriter writer = context.getWriter();
    final int latestRev = context.getBranch().getLatestRevision().getId();
    final String comment = args.comment.length == 0 ? null : args.comment[0];

    final LockTarget[] targets = new LockTarget[args.paths.length];
    for (int i = 0; i < args.paths.length; ++i) {
      final String path = context.getRepositoryPath(args.paths[i].path);
      final int rev = getRevision(args.paths[i].rev, latestRev);
      targets[i] = new LockTarget(path, rev);
    }

    final LockDesc[] locks;
    try {
      locks = context.getBranch().getRepository().wrapLockWrite(lockStorage -> {
        try {
          return lockStorage.lock(context.getUser(), context.getBranch(), comment, args.stealLock, targets);
        } catch (LockConflictException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_PATH_ALREADY_LOCKED, "Path is already locked: {0}", e.getLock().getPath()));
        }
      });
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

  @Override
  protected void permissionCheck(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    for (PathRev pathRev : args.paths)
      context.checkWrite(context.getRepositoryPath(pathRev.path));
  }

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
}
