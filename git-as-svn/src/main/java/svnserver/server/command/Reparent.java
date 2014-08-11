package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerWriter;

import java.io.IOException;

/**
 * Change current path in repository.
 * <p>
 * <pre>
 *   reparent
 *   params:   ( url:string )
 *   response: ( )
 * </pre>
 *
 * @author a.navrotskiy
 */
public class Reparent extends BaseCommand<Reparent.Params> {
  public static class Params {
    private final String url;

    public Params(String url) {
      this.url = url;
    }

    public String getUrl() {
      return url;
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
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }
}
