package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerWriter;

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
public class GetLatestRev extends BaseCommand<GetLatestRev.Params> {
  public static class Params {
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
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(42)
        .listEnd()
        .listEnd();
  }
}
