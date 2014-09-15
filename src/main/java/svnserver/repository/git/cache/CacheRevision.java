package svnserver.repository.git.cache;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * Revision cache information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class CacheRevision {
  @NotNull
  private final String message;
  @NotNull
  private final Map<String, String> renames = new TreeMap<>();

  public CacheRevision() {
    this.message = "";
  }

  public CacheRevision(@NotNull String message, @NotNull Map<String, String> renames) {
    this.message = message;
    this.renames.putAll(renames);
  }

  @NotNull
  public String getMessage() {
    return message;
  }

  @NotNull
  public Map<String, String> getRenames() {
    return renames;
  }
}
