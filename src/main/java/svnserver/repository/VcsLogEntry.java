package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;

import java.io.IOException;

/**
 * File modification information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsLogEntry {
  char getChange() throws IOException, SVNException;

  @NotNull
  SVNNodeKind getKind();

  boolean isContentModified() throws IOException, SVNException;

  boolean isPropertyModified() throws IOException, SVNException;
}
