package svnserver.config;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepository;
import svnserver.repository.git.GitPushMode;
import svnserver.repository.git.GitRepository;

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
  private boolean renameDetection = true;

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
  public Repository createRepository() throws IOException {
    final File file = new File(getPath()).getAbsoluteFile();
    if (!file.exists()) {
      throw new FileNotFoundException(file.getPath());
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
  public VcsRepository create() throws IOException, SVNException {
    return new GitRepository(createRepository(), createLinkedRepositories(), getPushMode(), getBranch(), isRenameDetection());
  }
}
