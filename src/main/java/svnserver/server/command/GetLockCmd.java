/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.eclipse.jgit.util.Holder;
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
 * get-lock
 *    params:    ( path:string )
 *    response:  ( [ lock:lockdesc ] )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GetLockCmd extends BaseCmd<GetLockCmd.Params> {

  @NotNull
  @Override
  public Class<? extends Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final SvnServerWriter writer = context.getWriter();
    final String path = context.getRepositoryPath(args.path);

    final Holder<LockDesc> holder = context.getBranch().getRepository().wrapLockRead(lockStorage -> {
      final Iterator<LockDesc> it = lockStorage.getLocks(context.getUser(), context.getBranch(), context.getRepositoryPath(path), Depth.Empty);
      return new Holder<>(it.hasNext() ? it.next() : null);
    });
    writer.listBegin()
        .word("success")
        .listBegin()
        .listBegin();
    LockCmd.writeLock(writer, holder.get());
    writer
        .listEnd()
        .listEnd()
        .listEnd();
  }

  @Override
  protected void permissionCheck(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    context.checkRead(context.getRepositoryPath(args.path));
  }

  public static final class Params {
    @NotNull
    private final String path;

    public Params(@NotNull String path) {
      this.path = path;
    }
  }
}
