package svnserver.repository;

import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Consumer with VCS exceptions.
 *
 * @author a.navrotskiy
 */
@FunctionalInterface
public interface VcsFunction<T, R> {
  R apply(T t) throws SVNException, IOException;
}
