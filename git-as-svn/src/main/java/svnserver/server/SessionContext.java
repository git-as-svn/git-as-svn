package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.Repository;
import svnserver.server.step.Step;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * SVN client session context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SessionContext {
  @NotNull
  private final SvnServerWriter writer;
  @NotNull
  private final Deque<Step> stepStack = new ArrayDeque<>();
  @NotNull
  private final Repository repository;

  public SessionContext(@NotNull SvnServerWriter writer, @NotNull Repository repository) {
    this.writer = writer;
    this.repository = repository;
  }

  @NotNull
  public Repository getRepository() {
    return repository;
  }

  @NotNull
  public SvnServerWriter getWriter() {
    return writer;
  }

  public void push(@NotNull Step step) {
    stepStack.push(step);
  }

  @Nullable
  public Step poll() {
    return stepStack.poll();
  }

}
