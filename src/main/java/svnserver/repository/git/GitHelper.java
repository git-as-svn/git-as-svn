package svnserver.repository.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Usefull methods.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitHelper {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepository.class);
  @NotNull
  private static final String HOOK_MESSAGE_PREFIX = "remote:";
  @NotNull
  private static final String SYSTEM_MESSAGE_PREFIX = "!";

  private GitHelper() {
  }

  public static SVNNodeKind getKind(@NotNull FileMode fileMode) {
    final int objType = fileMode.getObjectType();
    switch (objType) {
      case Constants.OBJ_TREE:
        return SVNNodeKind.DIR;
      case Constants.OBJ_BLOB:
        return SVNNodeKind.FILE;
      default:
        throw new IllegalStateException("Unknown obj type: " + objType);
    }
  }

  public static boolean pushLocal(@NotNull Repository repository, @NotNull ObjectId commitId, @NotNull Ref branch) throws SVNException {
    try {
      final Iterable<PushResult> results = new Git(repository)
          .push()
          .setRemote(".")
          .setRefSpecs(new RefSpec(commitId.name() + ":" + branch.getName()))
          .call();
      for (PushResult result : results) {
        for (RemoteRefUpdate remoteUpdate : result.getRemoteUpdates()) {
          switch (remoteUpdate.getStatus()) {
            case REJECTED_NONFASTFORWARD:
              return false;
            case OK:
              break;
            default:
              log.error("Unexpected push error: {}", remoteUpdate);
              throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, remoteUpdate.toString()));
          }
        }
      }
      return true;
    } catch (GitAPIException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e));
    }
  }

  public static boolean pushNative(@NotNull Repository repository, @NotNull ObjectId commitId, @NotNull Ref branch) throws IOException, SVNException {
    try {
      repository.getDirectory();
      final ProcessBuilder processBuilder = new ProcessBuilder("git", "push", "--porcelain", "--quiet", ".", commitId.name() + ":" + branch.getName())
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
}
