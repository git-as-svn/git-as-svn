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
import svnserver.repository.VcsCopyFrom;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.Arrays;

/**
 * Change current path in repository.
 * <p>
 * <pre>
 * get-locations
 *   params:   ( path:string peg-rev:number ( rev:number ... ) )
 * Before sending response, server sends location entries, ending with "done".
 *   location-entry: ( rev:number abs-path:number ) | done
 *   response: ( )
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class GetLocationsCmd extends BaseCmd<GetLocationsCmd.Params> {
  public static class Params {
    @NotNull
    private final String path;
    private final int pegRev;
    @NotNull
    private final int[] revs;

    public Params(@NotNull String path, int pegRev, @NotNull int[] revs) {
      this.path = path;
      this.pegRev = pegRev;
      this.revs = revs;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final SvnServerWriter writer = context.getWriter();

    final int[] sortedRevs = Arrays.copyOf(args.revs, args.revs.length);
    Arrays.sort(sortedRevs);
    String fullPath = context.getRepositoryPath(args.path);
    int lastChange = context.getRepository().getLastChange(fullPath, args.pegRev);
    if (lastChange < 0) {
      writer.word("done");
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: " + fullPath + "@" + args.pegRev));
    }
    for (int i = sortedRevs.length - 1; i >= 0; --i) {
      int revision = sortedRevs[i];
      if (revision > args.pegRev) {
        writer.word("done");
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: " + fullPath + "@" + args.pegRev + " at revision " + revision));
      }
      while ((revision < lastChange) && (lastChange >= 0)) {
        int change = context.getRepository().getLastChange(fullPath, lastChange - 1);
        if (change >= 0) {
          lastChange = change;
          continue;
        }
        final VcsCopyFrom copyFrom = context.getRepository().getRevisionInfo(lastChange).getCopyFrom(fullPath);
        if (copyFrom != null) {
          lastChange = copyFrom.getRevision();
          fullPath = copyFrom.getPath();
        } else {
          lastChange = -1;
          break;
        }
      }
      if (lastChange < 0)
        break;
      if (revision >= lastChange) {
        writer
            .listBegin()
            .number(revision)
            .string(fullPath)
            .listEnd();
      }
    }
    writer
        .word("done");
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }
}
