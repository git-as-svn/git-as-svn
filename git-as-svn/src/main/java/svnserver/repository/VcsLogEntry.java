package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNNodeKind;

/**
 * File modification information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsLogEntry {
  char getChange();

  @NotNull
  SVNNodeKind getKind();
}
