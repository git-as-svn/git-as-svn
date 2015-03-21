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
import svnserver.StreamHelper;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsFile;
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.io.InputStream;

/**
 * Get file content.
 * <p><pre>
 * get-file
 *    params:   ( path:string [ rev:number ] want-props:bool want-contents:bool
 *    ? want-iprops:bool )
 *    response: ( [ checksum:string ] rev:number props:proplist
 *    [ inherited-props:iproplist ] )
 *    If want-contents is specified, then after sending response, server
 *    sends file contents as a series of strings, terminated by the empty
 *    string, followed by a second empty command response to indicate
 *    whether an error occurred during the sending of the file.
 *    NOTE: the standard client doesn't send want-iprops as true, it uses
 *    get-iprops, but does send want-iprops as false to workaround a server
 *    bug in 1.8.0-1.8.8.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GetFileCmd extends BaseCmd<GetFileCmd.Params> {
  public static class Params {
    @NotNull
    private final String path;
    @NotNull
    private final int[] rev;
    private final boolean wantProps;
    private final boolean wantContents;
    /**
     * TODO: issue #30.
     */
    private final boolean wantIProps;

    public Params(@NotNull String path, @NotNull int[] rev, boolean wantProps, boolean wantContents, boolean wantIProps) {
      this.path = path;
      this.rev = rev;
      this.wantProps = wantProps;
      this.wantContents = wantContents;
      this.wantIProps = wantIProps;
    }
  }

  private static final int WINDOW_SIZE = 1024 * 100;

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    SvnServerWriter writer = context.getWriter();
    String fullPath = context.getRepositoryPath(args.path);

    if (fullPath.endsWith("/"))
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Could not cat all targets because some targets are directories"));

    final VcsRepository repository = context.getRepository();
    final VcsRevision revision = repository.getRevisionInfo(getRevision(args.rev, () -> repository.getLatestRevision().getId()));
    final VcsFile fileInfo = revision.getFile(fullPath);
    if (fileInfo == null)
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, fullPath + " not found in revision " + revision.getId()));

    if (fileInfo.isDirectory())
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, fullPath + " is a directory in revision " + revision.getId()));

    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin().string(fileInfo.getMd5()).listEnd() // md5
        .number(revision.getId()) // revision id
        .writeMap(args.wantProps ? fileInfo.getAllProperties() : null)
        .listEnd()
        .listEnd();
    if (args.wantContents) {
      byte[] buffer = new byte[WINDOW_SIZE];
      try (final InputStream stream = fileInfo.openStream()) {
        while (true) {
          int read = StreamHelper.readFully(stream, buffer, 0, buffer.length);
          writer.binary(buffer, 0, read);
          if (read == 0) {
            break;
          }
        }
      }
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
    }
  }

}
