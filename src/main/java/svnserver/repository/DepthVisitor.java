package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

/**
 * Visitor for Depth enumeration.
 *
 * @author a.navrotskiy
 */
public interface DepthVisitor<R> {
  @NotNull
  R visitEmpty() throws SVNException;

  @NotNull
  R visitFiles() throws SVNException;

  @NotNull
  R visitImmediates() throws SVNException;

  @NotNull
  R visitInfinity() throws SVNException;
}
