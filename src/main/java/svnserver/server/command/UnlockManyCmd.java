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
import svnserver.repository.locks.UnlockTarget;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * <pre>
 * unlock-many
 *    params:    ( break-lock:bool ( ( path:string [ token:string ] ) ... ) )
 *    Before sending response, server sends unlocked paths, ending with "done".
 *    pre-response: ( success ( path:string ) ) | ( failure ( err:error ) )
 *               | done
 *    response:  ( )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class UnlockManyCmd extends BaseCmd<UnlockManyCmd.Params> {
  @NotNull
  @Override
  public Class<? extends Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException {
    final SvnServerWriter writer = context.getWriter();

    final UnlockTarget[] targets = new UnlockTarget[args.paths.length];
    for (int i = 0; i < args.paths.length; ++i) {
      final PathToken pathToken = args.paths[i];
      final String path = context.getRepositoryPath(pathToken.path);
      final String lockToken = pathToken.lockToken.length == 0 ? null : pathToken.lockToken[0];
      targets[i] = new UnlockTarget(context.getRepositoryPath(path), lockToken);
    }
    try {
      context.getBranch().getRepository().wrapLockWrite(lockStorage -> {
        try {
          lockStorage.unlock(context.getUser(), context.getBranch(), args.breakLock, targets);
        } catch (LockConflictException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, e.getLock().getPath()));
        }
        return Boolean.TRUE;
      });
      for (PathToken path : args.paths)
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .string(path.path)
            .listEnd()
            .listEnd();
    } catch (SVNException e) {
      sendError(writer, e.getErrorMessage());
    }
    writer.word("done");
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }

  @Override
  protected void permissionCheck(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    for (PathToken pathRev : args.paths) {
      context.checkWrite(context.getRepositoryPath(pathRev.path));
    }
  }

  public static final class PathToken {
    @NotNull
    private final String path;
    @NotNull
    private final String[] lockToken;

    public PathToken(@NotNull String path, @NotNull String[] lockToken) {
      this.path = path;
      this.lockToken = lockToken;
    }
  }

  public static final class Params {
    private final boolean breakLock;
    @NotNull
    private final PathToken[] paths;

    public Params(boolean breakLock, @NotNull PathToken[] paths) {
      this.breakLock = breakLock;
      this.paths = paths;
    }
  }
}
