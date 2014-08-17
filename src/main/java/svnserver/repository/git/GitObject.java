package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

/**
 * Git object with repository information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitObject<T extends ObjectId> {
  @NotNull
  private final Repository repo;
  @NotNull
  private final T object;

  public GitObject(@NotNull Repository repo, @NotNull T object) {
    this.repo = repo;
    this.object = object;
  }

  @NotNull
  public Repository getRepo() {
    return repo;
  }

  @NotNull
  public T getObject() {
    return object;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitObject gitObject = (GitObject) o;

    return object.equals(gitObject.object);
  }

  @Override
  public int hashCode() {
    return object.hashCode();
  }
}
