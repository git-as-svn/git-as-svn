/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.step;

import org.jetbrains.annotations.NotNull;
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
    context.checkRead(context.getRepositoryPath(""));

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
