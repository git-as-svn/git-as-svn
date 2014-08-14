package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.*;

import java.io.IOException;

/**
 * Log tree builder (for debug only).
 *
 * @author a.navrotskiy
 */
public class LogCommitBuilder implements VcsCommitBuilder {

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LogCommitBuilder.class);
  private int depth = 0;

  @Override
  public void addDir(@NotNull String name) {
    log.info(indent() + "{} (create dir)", name);
    depth++;
  }

  @Override
  public void openDir(@NotNull String name) {
    log.info(indent() + "{} (modify dir)", name);
    depth++;
  }

  @Override
  public void delete(@NotNull String name, @NotNull VcsFile file) throws SVNException, IOException {
    log.info(indent() + "{} (delete)", name);
  }

  @Override
  public void saveFile(@NotNull String name, @NotNull VcsDeltaConsumer deltaConsumer) {
    log.info(indent() + name);
  }

  @Override
  public void closeDir() {
    depth--;
  }

  @Override
  public VcsRevision commit(@NotNull UserInfo userInfo, @NotNull String message) throws SVNException, IOException {
    log.info(indent() + "Commit: {}", message);
    return null;
  }

  @NotNull
  private String indent() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth * 2; ++i) sb.append(' ');
    return sb.toString();
  }
}
