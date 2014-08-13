package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import svnserver.SvnConstants;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.server.SessionContext;
import svnserver.server.error.ClientErrorException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Commit client changes.
 * <p><pre>
 * get-dir
 * commit
 *    params:   ( logmsg:string ? ( ( lock-path:string lock-token:string ) ... )
 *    keep-locks:bool ? rev-props:proplist )
 *    response: ( )
 *    Upon receiving response, client switches to editor command set.
 *    Upon successful completion of edit, server sends auth-request.
 *    After auth exchange completes, server sends commit-info.
 *    If rev-props is present, logmsg is ignored.  Only the svn:log entry in
 *    rev-props (if any) will be used.
 *    commit-info: ( new-rev:number date:string author:string
 *    ? ( post-commit-err:string ) )
 *    NOTE: when revving this, make 'logmsg' optional, or delete that parameter
 *    and have the log message specified in 'rev-props'.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */

public class CommitCmd extends BaseCmd<CommitCmd.CommitParams> {
  public static class CommitParams {
    @NotNull
    private final String message;

    public CommitParams(@NotNull String message) {
      this.message = message;
    }
  }

  public static class NoParams {
  }

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(DeltaCmd.class);

  @NotNull
  @Override
  public Class<CommitParams> getArguments() {
    return CommitParams.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull CommitParams args) throws IOException, ClientErrorException {
    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
    log.info("Enter editor mode");
    EditorPipeline pipeline = new EditorPipeline(args);
    pipeline.editorCommand(context);
  }

  public static class EditorPipeline {
    @NotNull
    private final Map<String, BaseCmd<?>> commands;

    public EditorPipeline(@NotNull CommitParams params) {
      commands = new HashMap<>();
      commands.put("close-edit", new LambdaCmd<>(NoParams.class, this::closeEdit));
    }

    private void closeEdit(@NotNull SessionContext context, @NotNull NoParams args) {
      //context.push(new CheckPermissionStep(this::complete));
    }

    private void editorCommand(@NotNull SessionContext context) throws IOException, ClientErrorException {
      final SvnServerParser parser = context.getParser();
      final SvnServerWriter writer = context.getWriter();
      parser.readToken(ListBeginToken.class);
      final String cmd = parser.readText();
      log.info("Editor command: {}", cmd);
      final BaseCmd command = commands.get(cmd);
      if (command != null) {
        Object param = MessageParser.parse(command.getArguments(), parser);
        parser.readToken(ListEndToken.class);
        //noinspection unchecked
        command.process(context, param);
      } else {
        log.error("Unsupported command: {}", cmd);
        BaseCmd.sendError(writer, SvnConstants.ERROR_UNIMPLEMENTED, "Unsupported command: " + cmd);
        parser.skipItems();
      }
    }
  }
}
