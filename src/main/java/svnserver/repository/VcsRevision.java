package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.Map;

/**
 * Revision info.
 *
 * @author a.navrotskiy
 */
public interface VcsRevision {
  int getId();

  @NotNull
  Map<String, String> getProperties();

  @NotNull
  String getDate();

  @NotNull
  String getAuthor();

  @NotNull
  String getLog();

  @Nullable
  VcsFile getFile(@NotNull String fullPath) throws IOException, SVNException;

  @NotNull
  Map<String, ? extends VcsLogEntry> getChanges();
}
