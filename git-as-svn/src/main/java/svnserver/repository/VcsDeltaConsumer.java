package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;

/**
 * VcsDeltaConsumer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsDeltaConsumer extends ISVNDeltaConsumer {
  void validateChecksum(@NotNull String md5) throws SVNException;
}
