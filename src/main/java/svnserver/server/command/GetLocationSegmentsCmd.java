package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsCopyFrom;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Change current path in repository.
 * <p>
 * <pre>
 * get-location-segments
 *   params:   ( path:string [ start-rev:number ] [ end-rev:number ] )
 * Before sending response, server sends location entries, ending with "done".
 *   location-entry: ( range-start:number range-end:number [ abs-path:string ] ) | done
 *   response: ( )
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class GetLocationSegmentsCmd extends BaseCmd<GetLocationSegmentsCmd.Params> {
  public static class Params {
    @NotNull
    private final String path;
    private final int[] pegRev;
    private final int[] startRev;
    private final int[] endRev;

    public Params(@NotNull String path, int[] pegRev, int[] startRev, int[] endRev) {
      this.path = path;
      this.pegRev = pegRev;
      this.startRev = startRev;
      this.endRev = endRev;
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
    final int endRev = getRevision(args.endRev, 0);
    final int pegRev = getRevision(args.pegRev, () -> context.getRepository().getLatestRevision().getId());
    final int startRev = getRevision(args.startRev, pegRev);
    if ((endRev > startRev) || (startRev > pegRev)) {
      writer.word("done");
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.getErrorCode(204900), "Invalid revision range: peg: " + pegRev + ", start: " + startRev + ", end " + endRev));
    }
    String fullPath = context.getRepositoryPath(args.path);
    final int lastChange = context.getRepository().getLastChange(fullPath, pegRev);
    if (lastChange < 0) {
      writer.word("done");
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: " + fullPath + "@" + pegRev));
    }
    int maxRev = pegRev;
    while (maxRev >= endRev) {
      int minRev = maxRev;
      while (true) {
        int change = context.getRepository().getLastChange(fullPath, minRev - 1);
        if (change > 0) {
          minRev = change;
        } else {
          break;
        }
      }
      if (minRev <= startRev) {
        writer
            .listBegin()
            .number(Math.max(minRev, endRev))
            .number(Math.min(maxRev, startRev))
            .listBegin().string(fullPath).listEnd()
            .listEnd();
      }
      final VcsCopyFrom copyFrom = context.getRepository().getRevisionInfo(minRev).getCopyFrom(fullPath);
      if (copyFrom != null) {
        maxRev = copyFrom.getRevision();
        fullPath = copyFrom.getPath();
      } else {
        break;
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
