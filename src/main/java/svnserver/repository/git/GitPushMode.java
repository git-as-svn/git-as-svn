/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Git push mode.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public enum GitPushMode {
  NATIVE {
    @Override
    public boolean push(@NotNull Repository repository, @NotNull ObjectId commitId, @NotNull String branch) throws IOException, SVNException {
      try {
        repository.getDirectory();
        final ProcessBuilder processBuilder = new ProcessBuilder("git", "push", "--porcelain", "--quiet", ".", commitId.name() + ":" + branch)
            .directory(repository.getDirectory())
            .redirectErrorStream(true);
        processBuilder.environment().put("LANG", "en_US.utf8");
        final Process process = processBuilder.start();
        final StringBuilder resultBuilder = new StringBuilder();
        final StringBuilder hookBuilder = new StringBuilder();
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
        int exitCode = process.waitFor();
        if (exitCode == 0) {
          return true;
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

  },
  SIMPLE {
    @Override
    public boolean push(@NotNull Repository repository, @NotNull ObjectId commitId, @NotNull String branch) throws SVNException, IOException {
      final RefUpdate refUpdate = repository.updateRef(branch);
      refUpdate.setNewObjectId(commitId);
      final RefUpdate.Result result = refUpdate.update();
      switch (result) {
        case REJECTED:
          return false;
        case NEW:
        case FAST_FORWARD:
          return true;
        default:
          log.error("Unexpected push error: {}", result);
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, result.name()));
      }
    }
  };
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitPushMode.class);
  @NotNull
  private static final String HOOK_MESSAGE_PREFIX = "remote:";
  @NotNull
  private static final String SYSTEM_MESSAGE_PREFIX = "!";

  public abstract boolean push(@NotNull Repository repository, @NotNull ObjectId commitId, @NotNull String branch) throws SVNException, IOException;

}
