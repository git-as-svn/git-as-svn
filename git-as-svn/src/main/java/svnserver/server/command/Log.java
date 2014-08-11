package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.parser.SvnServerWriter;

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
public class Log extends BaseCommand<Log.Params> {
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

    @NotNull
    public String[] getTargetPath() {
      return targetPath;
    }

    @Nullable
    public Integer getStartRev() {
      return startRev.length < 1 ? null : startRev[0];
    }

    @Nullable
    public Integer getEndRev() {
      return endRev.length < 1 ? null : endRev[0];
    }

    public boolean isChangedPaths() {
      return changedPaths;
    }

    public boolean isStrictNode() {
      return strictNode;
    }

    public int getLimit() {
      return limit;
    }

    public boolean isIncludeMergedRevisions() {
      return includeMergedRevisions;
    }

    @NotNull
    public String getRevpropsMode() {
      return revpropsMode;
    }

    @NotNull
    public String[] getRevprops() {
      return revprops;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  public void process(@NotNull SvnServerWriter writer, @NotNull Params args) throws IOException {
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin()
        .listEnd()
        .string("")
        .listEnd()
        .listEnd();
    for (int rev = 42; rev > 40; rev--) {
      writer
          .listBegin()
          .listBegin().listEnd()
          .number(rev);
      for (String revprop : args.revprops) {
        writer.listBegin();
        switch (revprop) {
          case "svn:author":
            writer.string("bozaro");
            break;
          case "svn:date":
            writer.string("2014-08-11T11:57:36.023610Z");
            break;
          case "svn:log":
            writer.string("Комментарий");
            break;
        }
        writer.listEnd();
      }
      writer
          .bool(false)
          .bool(false)
          .number(0)
          .listBegin()
          .listEnd()
          .listEnd();
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
