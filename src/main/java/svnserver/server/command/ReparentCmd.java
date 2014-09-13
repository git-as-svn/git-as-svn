package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.parser.SvnServerWriter;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Change current path in repository.
 * <p>
 * <pre>
 * reparent
 *    params:   ( url:string )
 *    response: ( )
 * </pre>
 *
 * @author a.navrotskiy
 */
public class ReparentCmd extends BaseCmd<ReparentCmd.Params> {
  public static class Params {
    @NotNull
    private final SVNURL url;

    public Params(@NotNull String url) throws SVNException {
      this.url = SVNURL.parseURIEncoded(url);
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    context.setParent(args.url);
    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }
}
