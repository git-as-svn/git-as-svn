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
import svnserver.repository.VcsLogEntry;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

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
public final class LogCmd extends BaseCmd<LogCmd.Params> {
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
    /**
     * TODO: issue #26.
     */
    private final boolean includeMergedRevisions;

    public Params(@NotNull String[] targetPath,
                  @NotNull int[] startRev,
                  @NotNull int[] endRev,
                  boolean changedPaths,
                  boolean strictNode,
                  int limit,
                  boolean includeMergedRevisions,
                  /**
                   * Broken-minded SVN feature we're unlikely to support EVER.
                   */
                  @SuppressWarnings("UnusedParameters")
                  @NotNull String revpropsMode,
                  @SuppressWarnings("UnusedParameters")
                  @NotNull String[] revprops) {
      this.targetPath = targetPath;
      this.startRev = startRev;
      this.endRev = endRev;
      this.changedPaths = changedPaths;
      this.strictNode = strictNode;
      this.limit = limit;
      this.includeMergedRevisions = includeMergedRevisions;
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

    if (startRev > head || endRev > head) {
      writer.word("done");
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + Math.max(startRev, endRev)));
    }

    final List<VcsRevision> log;
    if (startRev >= endRev) {
      log = getLog(context, args, startRev, endRev, args.limit);
    } else {
      final List<VcsRevision> logReverse = getLog(context, args, endRev, startRev, -1);
      final int minIndex = args.limit <= 0 ? 0 : Math.max(0, logReverse.size() - args.limit);
      log = new ArrayList<>(logReverse.size() - minIndex);
      for (int i = logReverse.size() - 1; i >= minIndex; i--) {
        log.add(logReverse.get(i));
      }
    }
    for (VcsRevision revisionInfo : log) {
      writer
          .listBegin()
          .listBegin();
      if (args.changedPaths) {
        final Map<String, ? extends VcsLogEntry> changes = revisionInfo.getChanges();
        writer.separator();
        for (Map.Entry<String, ? extends VcsLogEntry> entry : changes.entrySet()) {
          final VcsLogEntry logEntry = entry.getValue();
          final char change = logEntry.getChange();
          if (change == 0) continue;
          writer
              .listBegin()
              .string(entry.getKey()) // Path
              .word(change)
              .listBegin();
          final VcsCopyFrom copyFrom = logEntry.getCopyFrom();
          if (copyFrom != null) {
            writer.string(copyFrom.getPath());
            writer.number(copyFrom.getRevision());
          }
          writer.listEnd()
              .listBegin()
              .string(logEntry.getKind().toString())
              .bool(logEntry.isContentModified()) // text-mods
              .bool(logEntry.isPropertyModified()) // prop-mods
              .listEnd()
              .listEnd()
              .separator();
        }
      }

      final Map<String, String> revProps = revisionInfo.getProperties(false);

      writer.listEnd()
          .number(revisionInfo.getId())
          .listBegin().stringNullable(revisionInfo.getAuthor()).listEnd()
          .listBegin().stringNullable(revisionInfo.getDateString()).listEnd()
          .listBegin().stringNullable(revisionInfo.getLog()).listEnd()
          .bool(false)
          .bool(false)
          .number(revProps.size())
          .writeMap(revProps)
          .listEnd()
          .separator();
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

  private List<VcsRevision> getLog(@NotNull SessionContext context, @NotNull Params args, int endRev, int startRev, int limit) throws IOException, SVNException {
    final List<VcsCopyFrom> targetPaths = new ArrayList<>();
    int revision = -1;
    for (String target : args.targetPath) {
      final String fullTargetPath = context.getRepositoryPath(target);
      final int lastChange = context.getRepository().getLastChange(fullTargetPath, endRev);
      if (lastChange >= startRev) {
        targetPaths.add(new VcsCopyFrom(lastChange, fullTargetPath));
        revision = Math.max(revision, lastChange);
      }
    }
    final List<VcsRevision> result = new ArrayList<>();
    int logLimit = limit;
    while (revision >= startRev) {
      final VcsRevision revisionInfo = context.getRepository().getRevisionInfo(revision);
      result.add(revisionInfo);
      if (--logLimit == 0) break;

      int nextRevision = -1;
      final ListIterator<VcsCopyFrom> iter = targetPaths.listIterator();
      while (iter.hasNext()) {
        final VcsCopyFrom entry = iter.next();
        if (revision == entry.getRevision()) {
          final int lastChange = context.getRepository().getLastChange(entry.getPath(), revision - 1);
          if (lastChange >= revision) {
            throw new IllegalStateException();
          }
          if (lastChange < 0) {
            if (args.strictNode) {
              iter.remove();
              continue;
            }
            final VcsCopyFrom copyFrom = revisionInfo.getCopyFrom(entry.getPath());
            if (copyFrom != null) {
              iter.set(copyFrom);
              nextRevision = Math.max(nextRevision, copyFrom.getRevision());
            } else {
              iter.remove();
            }
          } else {
            iter.set(new VcsCopyFrom(lastChange, entry.getPath()));
            nextRevision = Math.max(nextRevision, lastChange);
          }
        }
      }
      revision = nextRevision;
    }
    return result;
  }
}
