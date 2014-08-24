package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsLogEntry;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
  public static class Params {
    @NotNull
    private final String[] targetPath;
    @NotNull
    private final int[] startRev;
    @NotNull
    private final int[] endRev;
    private final boolean changedPaths;
    /**
     * TODO: issue #26.
     */
    private final boolean strictNode;
    private final int limit;
    /**
     * TODO: issue #26.
     */
    private final boolean includeMergedRevisions;
    /**
     * TODO: issue #26.
     */
    @NotNull
    private final String revpropsMode;
    /**
     * TODO: issue #26.
     */
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
    final int head = context.getRepository().getLatestRevision().getId();
    int endRev = getRevision(args.endRev, head);
    int startRev = getRevision(args.startRev, 1);
    if ((startRev > head) || (endRev > head)) {
      writer.word("done");
      sendError(writer, SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + Math.max(startRev, endRev)));
      return;
    }

    final Set<String> targetPaths = new HashSet<>();
    for (String target : args.targetPath) {
      String fullTargetPath = context.getRepositoryPath(target);
      if (context.getRepository().getRevisionInfo(startRev).getFile(fullTargetPath) == null) {
        writer.word("done");
        sendError(writer, SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: revision " + startRev + ", path: " + fullTargetPath));
      }
      targetPaths.add(fullTargetPath);
    }

    int logLimit = args.limit;
    int step = startRev < endRev ? 1 : -1;
    for (int rev = startRev; rev != endRev + step; rev += step) {
      if (targetPaths.isEmpty())
        break;

      if (rev == 0)
        continue;

      final VcsRevision revisionInfo = context.getRepository().getRevisionInfo(rev);
      final Map<String, VcsLogEntry> changes = revisionInfo.getChanges();
      if (!hasTargets(changes, targetPaths)) continue;
      writer
          .listBegin()
          .listBegin();
      if (args.changedPaths) {
        writer.separator();
        for (Map.Entry<String, VcsLogEntry> entry : changes.entrySet()) {
          final VcsLogEntry logEntry = entry.getValue();
          final char change = logEntry.getChange();
          if (change == 0) continue;
          writer
              .listBegin()
              .string(entry.getKey()) // Path
              .word(change)
              .listBegin().listEnd() // todo: copy information.
              .listBegin()
              .string(logEntry.getKind().toString())
              .bool(logEntry.isContentModified()) // text-mods
              .bool(logEntry.isPropertyModified()) // prop-mods
              .listEnd()
              .listEnd()
              .separator();
        }
      }
      writer.listEnd()
          .number(rev)
          .listBegin().string(revisionInfo.getAuthor()).listEnd()
          .listBegin().string(revisionInfo.getDate()).listEnd()
          .listBegin().string(revisionInfo.getLog()).listEnd()
          .bool(false)
          .bool(false)
          .number(0)
          .listBegin()
          .listEnd()
          .listEnd()
          .separator();
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

  private static boolean hasTargets(Map<String, VcsLogEntry> changes, Set<String> targetPaths) {
    for (String targetPath : targetPaths) {
      if (changes.containsKey(targetPath) || targetPath.isEmpty()) return true;
    }
    return false;
  }
}
