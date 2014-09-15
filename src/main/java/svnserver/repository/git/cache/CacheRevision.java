package svnserver.repository.git.cache;

import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Revision cache information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class CacheRevision {
  @NotNull
  public static final CacheRevision empty = new CacheRevision();
  @NotNull
  private final String message;
  @NotNull
  private final Map<String, String> renames = new TreeMap<>();
  @NotNull
  private final Map<String, ObjectId> branches = new TreeMap<>();

  public CacheRevision() {
    this.message = "";
  }

  public CacheRevision(@NotNull String message, @NotNull Map<String, String> renames, @NotNull Map<String, ? extends ObjectId> branches) {
    this.message = message;
    this.renames.putAll(renames);
    this.branches.putAll(branches);
  }

  @NotNull
  public String getMessage() {
    return message;
  }

  @NotNull
  public Map<String, String> getRenames() {
    return Collections.unmodifiableMap(renames);
  }

  @NotNull
  public Map<String, ObjectId> getBranches() {
    return Collections.unmodifiableMap(branches);
  }
}
