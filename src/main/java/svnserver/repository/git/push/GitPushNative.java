/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.push;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.Loggers;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.LocalContext;
import svnserver.repository.VcsAccess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Git push by native git client.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitPushNative implements GitPusher {
  @NotNull
  private static final Logger log = Loggers.git;
  @NotNull
  private static final String HOOK_MESSAGE_PREFIX = "remote:";
  @NotNull
  private static final String SYSTEM_MESSAGE_PREFIX = "!";

  @NotNull
  private final LocalContext context;

  GitPushNative(@NotNull LocalContext context) {
    this.context = context;
  }

  @Override
  public boolean push(@NotNull Repository repository, @NotNull ObjectId commitId, @NotNull String branch, @NotNull User userInfo) throws SVNException, IOException {
    try {
      repository.getDirectory();
      final ProcessBuilder processBuilder = new ProcessBuilder("git", "push", "--porcelain", "--quiet", ".", commitId.name() + ":" + branch)
          .directory(repository.getDirectory())
          .redirectErrorStream(true);

      processBuilder.environment().put("LANG", "en_US.utf8");
      context.sure(VcsAccess.class).updateEnvironment(processBuilder.environment(), userInfo);

      final Process process = processBuilder.start();
      final StringBuilder resultBuilder = new StringBuilder();
      final StringBuilder hookBuilder = new StringBuilder();
      try {
        try (final BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          while (true) {
            final String line = stdout.readLine();
            if (line == null) {
              break;
            }
            if (line.startsWith(HOOK_MESSAGE_PREFIX)) {
              if (hookBuilder.length() > 0) hookBuilder.append('\n');
              hookBuilder.append(line.substring(HOOK_MESSAGE_PREFIX.length() + 1));
            }
            if (line.startsWith(SYSTEM_MESSAGE_PREFIX)) {
              // System message like:
              // !	2d1ed4dcc45bef07f6dfffabe7d3ff53aa147705:refs/heads/local	[remote rejected] (pre-receive hook declined)
              // !	75cad4dcb5f6982a1f2df073157f3aa2083ae272:refs/heads/local	[rejected] (non-fast-forward)
              if (resultBuilder.length() > 0) resultBuilder.append('\n');
              resultBuilder.append(line.substring(SYSTEM_MESSAGE_PREFIX.length() + 1));
            }
          }
        }
        final int exitCode = process.waitFor();
        if (exitCode == 0) {
          return true;
        }
      } finally {
        process.destroyForcibly();
      }
      final String resultMessage = resultBuilder.toString();
      if (resultMessage.contains("non-fast-forward")) {
        return false;
      }
      if (resultMessage.contains("hook")) {
        final String hookMessage = hookBuilder.toString();
        log.warn("Push rejected by hook:\n{}", hookMessage);
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.REPOS_HOOK_FAILURE, "Commit blocked by hook with output:\n" + hookMessage));
      }
      log.error("Unknown git push result:\n{}", resultMessage);
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, resultMessage));
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e));
    }
  }
}
