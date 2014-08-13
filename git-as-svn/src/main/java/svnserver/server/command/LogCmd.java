package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Change current path in repository.
 * <p>
 * <pre>
 * log
 *    params:   ( ( target-path:string ... ) [ start-rev:number ]
 *                [ end-rev:number ] changed-paths:bool strict-node:bool
 *                ? limit:number
 *                ? include-merged-revisions:bool
 *                all-revprops | revprops ( revprop:string ... ) )
 *    Before sending response, server sends log entries, ending with "done".
 *    If a client does not want to specify a limit, it should send 0 as the
 *    limit parameter.  rev-props excludes author, date, and log; they are
 *    sent separately for backwards-compatibility.
 *    log-entry: ( ( change:changed-path-entry ... ) rev:number
 *                 [ author:string ] [ date:string ] [ message:string ]
 *                 ? has-children:bool invalid-revnum:bool
 *                 revprop-count:number rev-props:proplist
 *                 ? subtractive-merge:bool )
 *             | done
 *    changed-path-entry: ( path:string A|D|R|M
 *                          ? ( ? copy-path:string copy-rev:number )
 *                          ? ( ? node-kind:string ? text-mods:bool prop-mods:bool ) )
 *    response: ( )
 * </pre>
 *
 * @author a.navrotskiy
 */
public class LogCmd extends BaseCmd<LogCmd.Params> {
  @SuppressWarnings("UnusedDeclaration")
  public static class Params {
    @NotNull
    private final String[] targetPath;
    @NotNull
    private final int[] startRev;
    @NotNull
    private final int[] endRev;
    private final boolean changedPaths;
    private final boolean strictNode;
    private final int limit;
    private final boolean includeMergedRevisions;
    @NotNull
    private final String revpropsMode;
    @NotNull
    private final String[] revprops;

    public Params(@NotNull String[] targetPath, @NotNull int[] startRev, @NotNull int[] endRev, boolean changedPaths,
                  boolean strictNode, int limit, boolean includeMergedRevisions,
                  @NotNull String revpropsMode, @NotNull String[] revprops) {
      this.targetPath = targetPath;
      this.startRev = startRev;
      this.endRev = endRev;
      this.changedPaths = changedPaths;
      this.strictNode = strictNode;
      this.limit = limit;
      this.includeMergedRevisions = includeMergedRevisions;
      this.revpropsMode = revpropsMode;
      this.revprops = revprops;
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
    final int head = context.getRepository().getLatestRevision();
    int startRev = getRevision(args.startRev, 1);
    int endRev = getRevision(args.endRev, head);
    int step = startRev < endRev ? 1 : -1;
    if ((startRev > head) || (endRev > head)) {
      writer.word("done");

      sendError(writer, SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + Math.max(startRev, endRev)));
      return;
    }

    int logLimit = args.limit;
    for (int rev = startRev; rev != endRev; rev += step) {
      final VcsRevision revisionInfo = context.getRepository().getRevisionInfo(rev);
      writer
          .listBegin()
          .listBegin().listEnd()
          .number(rev)
          .listBegin().string(revisionInfo.getAuthor()).listEnd()
          .listBegin().string(revisionInfo.getDate()).listEnd()
          .listBegin().string(revisionInfo.getLog()).listEnd()
          .bool(false)
          .bool(false)
          .number(0)
          .listBegin()
          .listEnd()
          .listEnd();
      if (--logLimit == 0) break;
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
