/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import svnserver.config.ConfigHelper;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Git submodules list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitSubmodules implements Shared {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitSubmodules.class);
  @NotNull
  private final Set<Repository> repositories = new CopyOnWriteArraySet<>();

  public GitSubmodules() {
  }

  public GitSubmodules(@NotNull File basePath, @NotNull Collection<String> paths) throws IOException {
    for (String path : paths) {
      final File file = ConfigHelper.joinPath(basePath, path).getAbsoluteFile();
      if (!file.exists()) {
        throw new FileNotFoundException(file.getPath());
      }
      log.info("Linked repository path: {}", file);
      repositories.add(new FileRepository(file));
    }
  }

  @Override
  public void init(@NotNull SharedContext context) {
  }

  @Nullable
  public GitObject<RevCommit> findCommit(@NotNull ObjectId objectId) throws IOException {
    for (Repository repo : repositories) {
      if (repo.hasObject(objectId)) {
        return new GitObject<>(repo, new RevWalk(repo).parseCommit(objectId));
      }
    }
    return null;
  }

  public void register(@NotNull Repository repository) {
    repositories.add(repository);
  }

  public void unregister(@NotNull Repository repository) {
    repositories.remove(repository);
  }
}
