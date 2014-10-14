/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepository;
import svnserver.repository.git.GitCreateMode;
import svnserver.repository.git.GitPushMode;
import svnserver.repository.git.GitRepository;
import svnserver.repository.locks.PersistentLockFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
public final class GitRepositoryConfig implements RepositoryConfig {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepositoryConfig.class);
  @NotNull
  private String branch = "master";
  @NotNull
  private File path = new File(".git");
  @NotNull
  private String[] submodules = {};
  @NotNull
  private GitPushMode pushMode = GitPushMode.NATIVE;
  @NotNull
  private GitCreateMode createMode = GitCreateMode.ERROR;

  private boolean renameDetection = true;

  @NotNull
  public String getBranch() {
    return branch;
  }

  public void setBranch(@NotNull String branch) {
    this.branch = branch;
  }

  public void setSubmodules(@NotNull String[] submodules) {
    this.submodules = submodules;
  }

  @NotNull
  public String[] getSubmodules() {
    return submodules;
  }

  @NotNull
  public GitPushMode getPushMode() {
    return pushMode;
  }

  public void setPushMode(@NotNull GitPushMode pushMode) {
    this.pushMode = pushMode;
  }

  public boolean isRenameDetection() {
    return renameDetection;
  }

  public void setRenameDetection(boolean renameDetection) {
    this.renameDetection = renameDetection;
  }

  @NotNull
  public GitCreateMode getCreateMode() {
    return createMode;
  }

  public void setCreateMode(@NotNull GitCreateMode createMode) {
    this.createMode = createMode;
  }

  @NotNull
  public Repository createRepository() throws IOException {
    if (!path.exists()) {
      log.info("Repository path: {} - not exists, create mode: {}", path, createMode);
      return createMode.createRepository(path, branch);
    }
    log.info("Repository path: {}", path);
    return new FileRepository(path);
  }

  @NotNull
  public List<Repository> createLinkedRepositories() throws IOException {
    final List<Repository> result = new ArrayList<>();
    for (String linkedPath : getSubmodules()) {
      final File file = new File(linkedPath).getAbsoluteFile();
      if (!file.exists()) {
        throw new FileNotFoundException(file.getPath());
      }
      log.info("Linked repository path: {}", file);
      final FileRepository linkedRepository = new FileRepository(file);
      result.add(linkedRepository);
    }
    return result;
  }

  @NotNull
  @Override
  public VcsRepository create(@NotNull DB cacheDb) throws IOException, SVNException {
    return new GitRepository(createRepository(), createLinkedRepositories(), getPushMode(), getBranch(), isRenameDetection(), new PersistentLockFactory(cacheDb), cacheDb);
  }
}
