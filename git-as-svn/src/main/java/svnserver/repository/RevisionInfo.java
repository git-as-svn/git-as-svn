package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * Revision info.
 *
 * @author a.navrotskiy
 */
public interface RevisionInfo {
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
  FileInfo getFile(@NotNull String fullPath) throws IOException;
}
