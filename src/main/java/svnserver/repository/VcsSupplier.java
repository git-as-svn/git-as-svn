package svnserver.repository;

import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Consumer with VCS exceptions.
 *
 * @author a.navrotskiy
 */
@FunctionalInterface
public interface VcsSupplier<T> {
  T get() throws SVNException, IOException;
}
