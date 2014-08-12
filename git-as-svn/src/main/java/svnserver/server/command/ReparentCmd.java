package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
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
    private final String url;

    public Params(String url) {
      this.url = url;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException {
    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }
}
