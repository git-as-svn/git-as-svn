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
public final class GetLatestRevCmd extends BaseCmd<NoParams> {

  @NotNull
  @Override
  public Class<NoParams> getArguments() {
    return NoParams.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull NoParams args) throws IOException {
    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(context.getRepository().getLatestRevision())
        .listEnd()
        .listEnd();
  }
}
