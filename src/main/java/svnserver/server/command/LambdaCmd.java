/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Simple lambda command.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LambdaCmd<T> extends BaseCmd<T> {
  @NotNull
  private final Class<T> type;
  @NotNull
  private final Callback<T> callback;

  LambdaCmd(@NotNull Class<T> type, @NotNull Callback<T> callback) {
    this.type = type;
    this.callback = callback;
  }

  @NotNull
  @Override
  public final Class<T> getArguments() {
    return type;
  }

  @Override
  public void process(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException {
    callback.processCommand(context, args);
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull T args) {
  }

  @Override
  protected void permissionCheck(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException {
    defaultPermissionCheck(context, args);
  }

  @FunctionalInterface
  public interface Callback<T> {
    void processCommand(@NotNull SessionContext context, @NotNull T args) throws IOException, SVNException;
  }
}
