package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerWriter;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Change current path in repository.
 * <p><pre>
 * get-latest-rev
 *    params:   ( )
 *    response: ( rev:number )
 * </pre>
 *
 * @author a.navrotskiy
 */
public class GetLatestRevCmd extends BaseCmd<GetLatestRevCmd.Params> {
  public static class Params {
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
        .number(42)
        .listEnd()
        .listEnd();
  }
}
