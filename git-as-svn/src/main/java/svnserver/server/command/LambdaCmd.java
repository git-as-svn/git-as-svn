package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;
import svnserver.server.error.ClientErrorException;

import java.io.IOException;

/**
 * Simple lambda command.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LambdaCmd<T> extends BaseCmd<T> {
  @FunctionalInterface
  public interface Callback<T> {
    void processCommand(@NotNull SessionContext context, @NotNull T args) throws IOException, ClientErrorException;
  }

  @NotNull
  private final Class<T> type;

  @NotNull
  private final Callback<T> callback;

  public LambdaCmd(@NotNull Class<T> type, @NotNull Callback<T> callback) {
    this.type = type;
    this.callback = callback;
  }

  @NotNull
  @Override
  public final Class<T> getArguments() {
    return type;
  }

  @Override
  public void process(@NotNull SessionContext context, @NotNull T args) throws IOException, ClientErrorException {
    callback.processCommand(context, args);
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull T args) throws IOException, ClientErrorException {
  }
}
