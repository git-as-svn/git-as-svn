package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
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

  public final void process(@NotNull SessionContext context, @NotNull T args) throws IOException {
    context.push(new CheckPermissionStep(sessionContext -> processCommand(sessionContext, args)));
  }

  /**
   * Process command.
   *
   * @param context Session context.
   * @param args    Command arguments.
   */
  protected abstract void processCommand(@NotNull SessionContext context, @NotNull T args) throws IOException;
}
