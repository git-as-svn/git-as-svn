package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Get revision property list.
 * <p>
 * <pre>
 * rev-proplist
 *    params:   ( rev:number )
 *    response: ( props:proplist )
 * </pre>
 *
 * @author a.navrotskiy
 */
public class RevPropListCmd extends BaseCmd<RevPropListCmd.Params> {

  public static class Params {
    private final int revision;

    public Params(int revision) {
      this.revision = revision;
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
    final VcsRevision revision = context.getRepository().getRevisionInfo(args.revision);
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .writeMap(revision.getProperties())
        .listEnd()
        .listEnd();
  }
}
