package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;

import java.util.Map;

/**
 * VcsDeltaConsumer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsDeltaConsumer extends ISVNDeltaConsumer {
  void validateChecksum(@NotNull String md5) throws SVNException;

  /**
   * Properties of copying/modifing node.
   *
   * @return Properties.
   */
  @NotNull
  Map<String, String> getProperties();
}
