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
import svnserver.repository.locks.LockManager;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * <pre>
 * get-lock
 *    params:    ( path:string )
 *    response:  ( [ lock:lockdesc ] )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GetLockCmd extends BaseCmd<GetLockCmd.Params> {

  public static final class Params {
    @NotNull
    private final String path;

    public Params(@NotNull String path) {
      this.path = path;
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
    final LockManager lockManager = context.getRepository().getLockManager();
    final String path = context.getRepositoryPath(args.path);
    final LockDesc lockDesc = lockManager.getLock(path);

    writer.listBegin()
        .word("success")
        .listBegin();
    LockCmd.writeLock(writer, lockDesc);
    writer
        .listEnd()
        .listEnd();
  }
}
