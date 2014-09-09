package svnserver.repository;

import org.jetbrains.annotations.NotNull;

/**
 * Copy from information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class VcsCopyFrom {
  private final int revision;
  @NotNull
  private final String path;

  private VcsCopyFrom() {
    revision = 0;
    path = "";
  }

  public VcsCopyFrom(int revision, @NotNull String path) {
    this.revision = revision;
    this.path = path;
  }

  public int getRevision() {
    return revision;
  }

  @NotNull
  public String getPath() {
    return path;
  }
}
