package svnserver.server.step;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Step for check permission.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class CheckPermissionStep implements Step {
  @NotNull
  private final Step nextStep;

  public CheckPermissionStep(@NotNull Step nextStep) {
    this.nextStep = nextStep;
  }

  @Override
  public void process(@NotNull SessionContext context) throws IOException, SVNException {
    if (!context.getAcl().check(context.getUser(), context.getRepositoryPath("")))
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED));

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
