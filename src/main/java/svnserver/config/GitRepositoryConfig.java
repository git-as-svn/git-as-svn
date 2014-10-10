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
import org.mapdb.TxMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepository;
import svnserver.repository.git.GitCreateMode;
import svnserver.repository.git.GitPushMode;
import svnserver.repository.git.GitRepository;
import svnserver.repository.locks.LockManagerType;

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
  private String path = ".git";
  @NotNull
  private String[] linked = {};
  @NotNull
  private GitPushMode pushMode = GitPushMode.NATIVE;
  @NotNull
  private GitCreateMode createMode = GitCreateMode.ERROR;

  private boolean renameDetection = true;
  @NotNull
  private LockManagerType lockManager = LockManagerType.Persistent;

  @NotNull
  public String getBranch() {
    return branch;
  }

  public void setBranch(@NotNull String branch) {
    this.branch = branch;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path;
  }

  public void setLinked(@NotNull String[] linked) {
    this.linked = linked;
  }

  @NotNull
  public String[] getLinked() {
    return linked;
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
  public LockManagerType getLockManager() {
    return lockManager;
  }

  public void setLockManager(@NotNull LockManagerType lockManager) {
    this.lockManager = lockManager;
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
    final File file = new File(getPath()).getAbsoluteFile();
    if (!file.exists()) {
      log.info("Repository path: {} - not exists, create mode: {}", file, createMode);
      return createMode.createRepository(file, branch);
    }
    log.info("Repository path: {}", file);
    return new FileRepository(file);
  }

  @NotNull
  public List<Repository> createLinkedRepositories() throws IOException {
    final List<Repository> result = new ArrayList<>();
    for (String linkedPath : getLinked()) {
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
  public VcsRepository create(@NotNull TxMaker cacheDb) throws IOException, SVNException {
    return new GitRepository(createRepository(), createLinkedRepositories(), getPushMode(), getBranch(), isRenameDetection(), lockManager.create(cacheDb));
  }
}
