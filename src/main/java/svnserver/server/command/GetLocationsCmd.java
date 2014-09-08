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
        if (change > 0) {
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
/*
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
  }*/
}
