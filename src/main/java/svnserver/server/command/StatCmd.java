/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.git.GitFile;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * <pre>
 *   stat
 *     params:   ( path:string [ rev:number ] )
 *     response: ( ? entry:dirent )
 *     dirent:   ( kind:node-kind size:number has-props:bool
 *                 created-rev:number [ created-date:string ]
 *                 [ last-author:string ] )
 *     New in svn 1.2.  If path is non-existent, an empty response is returned.
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class StatCmd extends BaseCmd<StatCmd.Params> {
  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final int revision = getRevisionOrLatest(args.rev, context);
    final String fullPath = context.getRepositoryPath(args.path);
    final GitFile file = context.getFile(revision, fullPath);

    context.getWriter()
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin();

    if (file != null) {
      context.getWriter()
          .listBegin()
          .word(file.getKind().toString()) // kind
          .number(file.getSize()) // size
          .bool(!file.getProperties().isEmpty()) // has properties
          .number(file.getLastChange().getId()) // last change revision
          .listBegin().stringNullable(file.getLastChange().getDateString()).listEnd() // last change date
          .listBegin().stringNullable(file.getLastChange().getAuthor()).listEnd() // last change author
          .listEnd();
    }

    context.getWriter()
        .listEnd()
        .listEnd()
        .listEnd();
  }

  public static class Params {
    @NotNull
    private final String path;
    @NotNull
    private final int[] rev;

    public Params(@NotNull String path, @NotNull int[] rev) {
      this.path = path;
      this.rev = rev;
    }
  }
}
