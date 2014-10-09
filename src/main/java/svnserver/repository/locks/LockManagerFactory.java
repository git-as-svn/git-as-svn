package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepository;

import java.io.IOException;

/**
 * Lock manager factory.
 *
 * @author a.navrotskiy
 */
public interface LockManagerFactory {
  @NotNull
  <T> T wrapLockRead(@NotNull VcsRepository repository, @NotNull LockWorker<T, LockManagerRead> work) throws IOException, SVNException;

  @NotNull
  <T> T wrapLockWrite(@NotNull VcsRepository repository, @NotNull LockWorker<T, LockManagerWrite> work) throws IOException, SVNException;
}
