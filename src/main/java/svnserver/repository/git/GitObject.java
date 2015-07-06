/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

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
    return AnyObjectId.equals(object, gitObject.object);
  }

  @NotNull
  public ObjectLoader openObject() throws IOException {
    return repo.newObjectReader().open(object);
  }

  @Override
  public int hashCode() {
    return object.hashCode();
  }

  @Override
  public String toString() {
    return object.name();
  }
}
