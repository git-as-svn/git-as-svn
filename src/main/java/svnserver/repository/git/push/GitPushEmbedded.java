/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.push;

import com.google.common.base.Strings;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;

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
  private final String hooksPathOverride;

  public GitPushEmbedded(@NotNull LocalContext context, @Nullable String hooksPathOverride) {
    this.context = context;
    this.hooksPathOverride = hooksPathOverride;
  }

  @NotNull
  private static String getObjectId(@Nullable ObjectId objectId) {
    return objectId == null ? ObjectId.zeroId().getName() : objectId.getName();
  }

  @Override
  public boolean push(@NotNull Repository repository, @NotNull ObjectId ReceiveId, @NotNull String branch, @NotNull User userInfo) throws SVNException, IOException {
    final RefUpdate refUpdate = repository.updateRef(branch);
    refUpdate.getOldObjectId();
    refUpdate.setNewObjectId(ReceiveId);
    runReceiveHook(repository, refUpdate, SVNErrorCode.REPOS_HOOK_FAILURE, "pre-receive", userInfo);
    runUpdateHook(repository, refUpdate, "update", userInfo);
    final RefUpdate.Result result = refUpdate.update();
    switch (result) {
      case REJECTED:
      case LOCK_FAILURE:
        return false;
      case NEW:
      case FAST_FORWARD:
        runReceiveHook(repository, refUpdate, SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED, "post-receive", userInfo);
        return true;
      default:
        log.error("Unexpected push error: {}", result);
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, result.name()));
    }
  }

  private void runReceiveHook(@NotNull Repository repository, @NotNull RefUpdate refUpdate, @NotNull SVNErrorCode svnErrorCode, @NotNull String hook, @NotNull User userInfo) throws SVNException {
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

  private void runUpdateHook(@NotNull Repository repository, @NotNull RefUpdate refUpdate, @NotNull String hook, @NotNull User userInfo) throws SVNException {
    runHook(repository, SVNErrorCode.REPOS_HOOK_FAILURE, hook, userInfo, processBuilder -> {
      processBuilder.command().addAll(Arrays.asList(
          refUpdate.getName(),
          getObjectId(refUpdate.getOldObjectId()),
          getObjectId(refUpdate.getNewObjectId())
      ));
      return processBuilder.start();
    });
  }

  private void runHook(@NotNull Repository repository, @NotNull SVNErrorCode hookErrorCode, @NotNull String hook, @NotNull User userInfo, @NotNull HookRunner runner) throws SVNException {
    final Path repositoryDir = repository.getDirectory() == null ? null : repository.getDirectory().toPath();
    if (repositoryDir == null)
      // We don't have a dir where to run the hook :(
      return;

    final String hooksPath = getHooksPath(repository);
    final Path hooksDir = ConfigHelper.joinPath(repositoryDir, hooksPath);
    final Path script = ConfigHelper.joinPath(hooksDir, hook);

    final long startTime = System.currentTimeMillis();
    if (!Files.exists(script))
      return;

    final ProcessBuilder processBuilder = new ProcessBuilder(script.toString())
        .directory(repositoryDir.toFile())
        .redirectErrorStream(true);

    processBuilder.environment().put("LANG", "en_US.utf8");
    userInfo.updateEnvironment(processBuilder.environment());
    context.getShared().sure(UserDB.class).updateEnvironment(processBuilder.environment(), userInfo);
    context.sure(VcsAccess.class).updateEnvironment(processBuilder.environment());

    Process process = null;
    try {
      process = runner.exec(processBuilder);

      // Prevent hanging if hook tries to read from stdin
      process.getOutputStream().close();

      final String hookMessage;
      try (Reader stdout = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        hookMessage = CharStreams.toString(stdout);
      }

      final int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new SVNException(SVNErrorMessage.create(hookErrorCode, String.format("Hook %s failed with output:\n%s", script, hookMessage)));
      }
    } catch (InterruptedException | IOException e) {
      log.error("Hook failed: " + script, e);
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e));
    } finally {
      final long endTime = System.currentTimeMillis();
      log.info("{} hook for repository {} took {}ms", hook, repository.toString(), (endTime - startTime));

      if (process != null)
        process.destroyForcibly();
    }
  }

  @NotNull
  private String getHooksPath(@NotNull Repository repository) {
    if (!Strings.isNullOrEmpty(hooksPathOverride))
      return hooksPathOverride;

    final String hooksPathFromConfig = repository.getConfig().getString(CONFIG_CORE_SECTION, null, "hooksPath");
    if (!Strings.isNullOrEmpty(hooksPathFromConfig))
      return hooksPathFromConfig;

    return "hooks";
  }

  @FunctionalInterface
  public interface HookRunner {
    @NotNull
    Process exec(@NotNull ProcessBuilder processBuilder) throws IOException;
  }
}
