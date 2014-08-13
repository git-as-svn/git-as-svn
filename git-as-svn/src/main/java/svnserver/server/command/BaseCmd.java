package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.server.SessionContext;
import svnserver.server.step.CheckPermissionStep;

import java.io.IOException;

/**
 * SVN client command base class.
 * Must be stateless and thread-safe.
 *
 * @author a.navrotskiy
 */
public abstract class BaseCmd<T> {
  /**
   * Arguments class.
   *
   * @return Arguments class.
   */
  @NotNull
  public abstract Class<T> getArguments();

  public void process(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException {
    context.push(new CheckPermissionStep(sessionContext -> processCommand(sessionContext, args)));
  }

  /**
   * Process command.
   *
   * @param context Session context.
   * @param args    Command arguments.
   */
  protected abstract void processCommand(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException;

  protected int getRevision(int[] rev, int defaultRevision) {
    return rev.length > 0 ? rev[0] : defaultRevision;
  }

  public static void sendError(@NotNull SvnServerWriter writer, @NotNull SVNErrorMessage errorMessage) throws IOException {
    sendError(writer, errorMessage.getErrorCode().getCode(), errorMessage.getMessage());
  }

  public static void sendError(@NotNull SvnServerWriter writer, int code, @NotNull String msg) throws IOException {
    writer
        .listBegin()
        .word("failure")
        .listBegin()
        .listBegin()
        .number(code)
        .string(msg)
        .string("...")
        .number(0)
        .listEnd()
        .listEnd()
        .listEnd();
  }
}
