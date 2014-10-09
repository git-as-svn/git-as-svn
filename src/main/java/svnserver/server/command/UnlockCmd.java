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
import svnserver.repository.locks.UnlockTarget;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * <pre>
 * unlock
 *    params:    ( path:string [ token:string ] break-lock:bool )
 *    response:  ( )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class UnlockCmd extends BaseCmd<UnlockCmd.Params> {

  public static final class Params {
    @NotNull
    private final String path;
    @NotNull
    private final String[] lockToken;
    private final boolean breakLock;

    public Params(@NotNull String path, @NotNull String[] lockToken, boolean breakLock) {
      this.path = path;
      this.lockToken = lockToken;
      this.breakLock = breakLock;
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
    final String lockToken = args.lockToken.length == 0 ? null : args.lockToken[0];
    context.getRepository().wrapLockWrite((lockManager) -> {
      lockManager.unlock(context, args.breakLock, new UnlockTarget[]{new UnlockTarget(context.getRepositoryPath(path), lockToken)});
      return Boolean.TRUE;
    });
    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }
}
