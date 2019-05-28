/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
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
  public static void sendError(@NotNull SvnServerWriter writer, @NotNull SVNErrorMessage errorMessage) throws IOException {
    writer
        .listBegin()
        .word("failure")
        .listBegin()
        .listBegin()
        .number(errorMessage.getErrorCode().getCode())
        .string(errorMessage.getMessageTemplate())
        .string("")
        .number(0)
        .listEnd()
        .listEnd()
        .listEnd();
  }

  /**
   * Arguments class.
   *
   * @return Arguments class.
   */
  @NotNull
  public abstract Class<? extends T> getArguments();

  public void process(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException {
    context.push(new CheckPermissionStep(sessionContext -> processCommand(sessionContext, args), sessionContext -> permissionCheck(sessionContext, args)));
  }

  /**
   * Process command.
   *
   * @param context Session context.
   * @param args    Command arguments.
   */
  protected abstract void processCommand(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException;

  /**
   * Check permissions for this command.
   *
   * @param context Session context.
   * @param args    Command arguments.
   */
  protected void permissionCheck(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException {
    context.checkRead(context.getRepositoryPath(""));
  }

  protected int getRevision(@NotNull int[] rev, int defaultRevision) {
    if (rev.length > 0) {
      final int revNum = rev[0];
      if (revNum >= 0)
        return revNum;
    }

    return defaultRevision;
  }

  int getRevisionOrLatest(@NotNull int[] rev, @NotNull SessionContext context) {
    if (rev.length > 0) {
      final int revNum = rev[0];
      if (revNum >= 0)
        return revNum;
    }

    return context.getBranch().getLatestRevision().getId();
  }
}
