package svnserver.repository.git.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Direct reference mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class RefMappingDirect implements RefMapping {
  @NotNull
  private final String gitName;
  @NotNull
  private final String svnPath;

  public RefMappingDirect(@NotNull String gitName, @NotNull String svnPath) {
    this.gitName = gitName;
    this.svnPath = svnPath;
  }

  @Nullable
  @Override
  public String gitToSvn(@NotNull String gitName) {
    return this.gitName.equals(gitName) ? svnPath : null;
  }

  @Nullable
  @Override
  public String svnToGit(@NotNull String svnPath) {
    return this.svnPath.equals(svnPath) ? gitName : null;
  }
}
