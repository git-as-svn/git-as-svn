package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;

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

  long getDate();

  @NotNull
  default String getDateString() {
    return StringHelper.formatDate(getDate());
  }

  @Nullable
  String getAuthor();

  @Nullable
  String getLog();

  @Nullable
  VcsFile getFile(@NotNull String fullPath) throws IOException, SVNException;

  @NotNull
  Map<String, ? extends VcsLogEntry> getChanges() throws IOException, SVNException;

  @Nullable
  VcsCopyFrom getCopyFrom(@NotNull String fullPath);
}
