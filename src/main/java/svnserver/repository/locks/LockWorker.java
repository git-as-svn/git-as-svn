package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Worker for execute some work with lock information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@FunctionalInterface
public interface LockWorker<T, M extends LockManagerRead> {
  @NotNull
  T exec(@NotNull M lockManager) throws SVNException, IOException;
}
