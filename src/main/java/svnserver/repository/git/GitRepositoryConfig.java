package svnserver.repository.git;

import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Git repository configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface GitRepositoryConfig {
  @NotNull
  String getBranch();

  @NotNull
  Repository createRepository() throws IOException;

  @NotNull
  List<Repository> createLinkedRepositories() throws IOException;

  @NotNull
  GitPushMode getPushMode();
}
