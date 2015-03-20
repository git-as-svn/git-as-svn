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
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Get file content.
 * <p><pre>
 * get-dir
 *    params:   ( path:string [ rev:number ] want-props:bool want-contents:bool
 *    ? ( field:dirent-field ... ) ? want-iprops:bool )
 *    response: ( rev:number props:proplist ( entry:dirent ... )
 *    [ inherited-props:iproplist ] )]
 *    dirent:   ( name:string kind:node-kind size:number has-props:bool
 *    created-rev:number [ created-date:string ]
 *    [ last-author:string ] )
 *    dirent-field: kind | size | has-props | created-rev | time | last-author
 *    | word
 *    NOTE: the standard client doesn't send want-iprops as true, it uses
 *    get-iprops, but does send want-iprops as false to workaround a server
 *    bug in 1.8.0-1.8.8.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GetDirCmd extends BaseCmd<GetDirCmd.Params> {
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

    public Params(@NotNull String path,
                  @NotNull int[] rev,
                  boolean wantProps,
                  boolean wantContents,
                  /**
                   * This is a broken-minded SVN feature we are unlikely to support ever.
                   * <p>
                   * Client can declare what fields it wants to be sent for child nodes (wantContents=true).
                   * <p>
                   * However,
                   * <ul>
                   * <li>fields are not optional, so we still have fill them with junk values</li>
                   * <li>They're trivial to calculate.</li>
                   * <li>For additional lulz, see the email thread on dev@svn, 2012-03-28, subject
                   * "buildbot failure in ASF Buildbot on svn-slik-w2k3-x64-ra",
                   * &lt;http://svn.haxx.se/dev/archive-2012-03/0655.shtml&gt;.</li>
                   * </ul>
                   */
                  @SuppressWarnings("UnusedParameters")
                  @NotNull String[] fields,
                  boolean wantIProps) {
      this.path = path;
      this.rev = rev;
      this.wantProps = wantProps;
      this.wantContents = wantContents;
      this.wantIProps = wantIProps;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    SvnServerWriter writer = context.getWriter();
    final String fullPath = context.getRepositoryPath(args.path);
    final VcsRepository repository = context.getRepository();
    final VcsRevision revision = repository.getRevisionInfo(getRevision(args.rev, () -> repository.getLatestRevision().getId()));
    final VcsFile fileInfo = revision.getFile(fullPath);

    if (fileInfo == null)
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, fullPath + " not found in revision " + revision.getId()));

    if (!fileInfo.isDirectory())
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, fullPath + " is not a directory in revision " + revision.getId()));

    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(revision.getId()) // rev
        .writeMap(args.wantProps ? fileInfo.getAllProperties() : null) // props
        .listBegin()
        .separator();
    if (args.wantContents) {
      for (VcsFile item : fileInfo.getEntries()) {
        final VcsRevision lastChange = item.getLastChange();
        writer
            .listBegin()
            .string(item.getFileName()) // name
            .word(item.getKind().toString()) // node-kind
            .number(item.getSize()) // size
            .bool(!item.getProperties().isEmpty()) // has-props
            .number(lastChange.getId()) // created-rev
            .listBegin().stringNullable(lastChange.getDateString()).listEnd() // created-date
            .listBegin().stringNullable(lastChange.getAuthor()).listEnd() // last-author
            .listEnd()
            .separator();
      }
    }
    writer
        .listEnd()
        .listEnd()
        .listEnd();
  }
}
