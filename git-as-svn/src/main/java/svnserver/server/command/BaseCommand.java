package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerWriter;

import java.io.IOException;

/**
 * Simple code.
 *
 * @author a.navrotskiy
 */
public abstract class BaseCommand<T> {
  /**
   * Arguments class.
   *
   * @return Arguments class.
   */
  @NotNull
  public abstract Class<T> getArguments();

  /**
   * Process command.
   *
   * @param args   Command arguments.
   * @param writer Writer.
   */
  public abstract void process(@NotNull SvnServerWriter writer, @NotNull T args) throws IOException;
}
