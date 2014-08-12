package svnserver.server.step;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;
import svnserver.server.error.ClientErrorException;

import java.io.IOException;

/**
 * Step for check permission.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class CheckPermissionStep implements Step {
  @NotNull
  private final Step nextStep;

  public CheckPermissionStep(@NotNull Step nextStep) {
    this.nextStep = nextStep;
  }

  @Override
  public void process(@NotNull SessionContext context) throws IOException, ClientErrorException {
    context.getWriter()
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin()
        .listEnd()
        .string("")
        .listEnd()
        .listEnd();
    nextStep.process(context);
  }
}
