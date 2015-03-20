/**
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
import svnserver.repository.VcsFile;
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Change current path in repository.
 * <p>
 * <pre>
 * stat
 *    params:   ( path:string [ rev:number ] )
 *    response: ( ? entry:dirent )
 *    dirent:   ( name:string kind:node-kind size:number has-props:bool
 *    created-rev:number [ created-date:string ]
 *    [ last-author:string ] )
 *    New in svn 1.2.  If path is non-existent, an empty response is returned.
 * </pre>
 *
 * @author a.navrotskiy
 */
public class StatCmd extends BaseCmd<StatCmd.Params> {
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

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final String fullPath = context.getRepositoryPath(args.path);
    final VcsRepository repository = context.getRepository();
    final VcsRevision revision = repository.getRevisionInfo(getRevision(args.rev, repository.getLatestRevision().getId()));
    final VcsFile fileInfo = revision.getFile(fullPath);

    if (fileInfo == null)
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, fullPath + " not found in revision " + revision.getId()));

    context.getWriter()
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin()
        .listBegin()
        .word(fileInfo.getKind().toString()) // kind
        .number(fileInfo.getSize()) // size
        .bool(!fileInfo.getProperties().isEmpty()) // has properties
        .number(fileInfo.getLastChange().getId()) // last change revision
        .listBegin().stringNullable(fileInfo.getLastChange().getDateString()).listEnd() // last change date
        .listBegin().stringNullable(fileInfo.getLastChange().getAuthor()).listEnd() // last change author
        .listEnd()
        .listEnd()
        .listEnd()
        .listEnd();
  }
}
