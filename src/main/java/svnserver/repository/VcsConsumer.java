package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Consumer with VCS exceptions.
 *
 * @author a.navrotskiy
 */
@FunctionalInterface
public interface VcsConsumer<T> {
  void accept(@NotNull T arg) throws SVNException, IOException;
}
