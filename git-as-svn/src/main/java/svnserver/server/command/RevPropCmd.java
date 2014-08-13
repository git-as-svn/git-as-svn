package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.RevisionInfo;
import svnserver.server.SessionContext;
import svnserver.server.error.ClientErrorException;

import java.io.IOException;

/**
 * Get revision property.
 * <p>
 * <pre>
 * rev-prop
 *    params:   ( rev:number name:string )
 *    response: ( [ value:string ] )
 * </pre>
 *
 * @author a.navrotskiy
 */
public class RevPropCmd extends BaseCmd<RevPropCmd.Params> {
  @SuppressWarnings("UnusedDeclaration")
  public static class Params {
    private final int revision;
    @NotNull
    private final String propName;

    public Params(int revision, @NotNull String propName) {
      this.revision = revision;
      this.propName = propName;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, ClientErrorException {
    final SvnServerWriter writer = context.getWriter();
    final RevisionInfo revision = context.getRepository().getRevisionInfo(args.revision);
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin();
    final String propValue = revision.getProperties().get(args.propName);
    if (propValue != null) {
      writer.string(propValue);
    }
    writer
        .listEnd()
        .listEnd()
        .listEnd();
  }
}
