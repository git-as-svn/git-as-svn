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
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsFile;
import svnserver.repository.VcsRevision;
import svnserver.repository.git.GitRepository;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Get file content.
 * <p><pre>
 * get-iprops
 *    params:   ( path:string [ rev:number ] )
 *    response: ( inherited-props:iproplist )
 *    New in svn 1.8.  If rev is not specified, the youngest revision is used.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GetIPropsCmd extends BaseCmd<GetIPropsCmd.Params> {
  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    SvnServerWriter writer = context.getWriter();
    String fullPath = context.getRepositoryPath(args.path);

    final GitRepository repository = context.getRepository();
    final VcsRevision info = repository.getRevisionInfo(getRevisionOrLatest(args.rev, context));
    final List<VcsFile> files = new ArrayList<>();
    int index = -1;
    while (true) {
      index = fullPath.indexOf('/', index + 1);
      if (index < 0) {
        break;
      }
      final String subPath = fullPath.substring(0, index);
      final VcsFile fileInfo = info.getFile(subPath);
      if (fileInfo == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, subPath));
      }
      files.add(fileInfo);
    }
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin();
    for (VcsFile file : files) {
      writer
          .listBegin()
          .string(file.getFullPath())
          .writeMap(file.getProperties())
          .listEnd();
    }
    writer
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
