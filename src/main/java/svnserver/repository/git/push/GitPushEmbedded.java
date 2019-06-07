/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.push;

import com.google.common.io.CharStreams;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.Loggers;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.config.ConfigHelper;
import svnserver.context.LocalContext;
import svnserver.repository.VcsAccess;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Git push mode.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitPushEmbedded implements GitPusher {
  @NotNull
  private static final Logger log = Loggers.git;

  @NotNull
  private final LocalContext context;
  @Nullable
  private final String preReceive;
  @Nullable
  private final String postReceive;
  @Nullable
  private final String update;

  public GitPushEmbedded(@NotNull LocalContext context, @Nullable String preReceive, @Nullable String postReceive, @Nullable String update) {
    this.context = context;
    this.preReceive = preReceive;
    this.postReceive = postReceive;
    this.update = update;
  }

  @Override
  public boolean push(@NotNull Repository repository, @NotNull ObjectId ReceiveId, @NotNull String branch, @NotNull User userInfo) throws SVNException, IOException {
    final RefUpdate refUpdate = repository.updateRef(branch);
    refUpdate.getOldObjectId();
    refUpdate.setNewObjectId(ReceiveId);
    runReceiveHook(repository, refUpdate, SVNErrorCode.REPOS_HOOK_FAILURE, preReceive, userInfo);
    runUpdateHook(repository, refUpdate, update, userInfo);
    final RefUpdate.Result result = refUpdate.update();
    switch (result) {
      case REJECTED:
      case LOCK_FAILURE:
        return false;
      case NEW:
      case FAST_FORWARD:
        runReceiveHook(repository, refUpdate, SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED, postReceive, userInfo);
        return true;
      default:
        log.error("Unexpected push error: {}", result);
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, result.name()));
    }
  }

  private void runReceiveHook(@NotNull Repository repository, @NotNull RefUpdate refUpdate, @NotNull SVNErrorCode svnErrorCode, @Nullable String hook, @NotNull User userInfo) throws IOException, SVNException {
    runHook(repository, svnErrorCode, hook, userInfo, processBuilder -> {
      final Process process = processBuilder.start();
      try (Writer stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
        stdin.write(getObjectId(refUpdate.getOldObjectId()));
        stdin.write(' ');
        stdin.write(getObjectId(refUpdate.getNewObjectId()));
        stdin.write(' ');
        stdin.write(refUpdate.getName());
        stdin.write('\n');
      }
      return process;
    });
  }

  private void runUpdateHook(@NotNull Repository repository, @NotNull RefUpdate refUpdate, @Nullable String hook, @NotNull User userInfo) throws IOException, SVNException {
    runHook(repository, SVNErrorCode.REPOS_HOOK_FAILURE, hook, userInfo, processBuilder -> {
      processBuilder.command().addAll(Arrays.asList(
          refUpdate.getName(),
          getObjectId(refUpdate.getOldObjectId()),
          getObjectId(refUpdate.getNewObjectId())
      ));
      return processBuilder.start();
    });
  }

  private void runHook(@NotNull Repository repository, @NotNull SVNErrorCode hookErrorCode, @Nullable String hook, @NotNull User userInfo, @NotNull HookRunner runner) throws IOException, SVNException {
    if (hook == null || hook.isEmpty()) {
      return;
    }
    final File script = ConfigHelper.joinPath(ConfigHelper.joinPath(repository.getDirectory(), "hooks"), hook);

    final long startTime = System.currentTimeMillis();
    if (script.isFile()) {
      final ProcessBuilder processBuilder = new ProcessBuilder(script.getAbsolutePath())
          .directory(repository.getDirectory())
          .redirectErrorStream(true);

      processBuilder.environment().put("LANG", "en_US.utf8");
      userInfo.updateEnvironment(processBuilder.environment());
      context.getShared().sure(UserDB.class).updateEnvironment(processBuilder.environment(), userInfo);
      context.sure(VcsAccess.class).updateEnvironment(processBuilder.environment());

      final Process process = runner.exec(processBuilder);

      // Prevent hanging if hook tries to read from stdin
      process.getOutputStream().close();

      try {
        final String hookMessage;
        try (Reader stdout = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
          hookMessage = CharStreams.toString(stdout);
        }

        final int exitCode = process.waitFor();
        if (exitCode != 0) {
          throw new SVNException(SVNErrorMessage.create(hookErrorCode, String.format("Hook %s failed with output:\n%s", script.getAbsolutePath(), hookMessage)));
        }
      } catch (InterruptedException e) {
        log.error("Hook interrupted: " + script.getAbsolutePath(), e);
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e));
      } finally {
        final long endTime = System.currentTimeMillis();
        log.info("{} hook for repository {} took {}ms", hook, repository.toString(), (endTime - startTime));

        process.destroyForcibly();
      }
    }
  }

  @NotNull
  private static String getObjectId(@Nullable ObjectId objectId) {
    return objectId == null ? ObjectId.zeroId().getName() : objectId.getName();
  }

  @FunctionalInterface
  public interface HookRunner {
    @NotNull
    Process exec(@NotNull ProcessBuilder processBuilder) throws IOException;
  }
}
