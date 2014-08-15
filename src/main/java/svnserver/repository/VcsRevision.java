package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

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
  VcsFile getFile(@NotNull String fullPath) throws IOException;

  @NotNull
  Map<String, VcsLogEntry> getChanges();
}
