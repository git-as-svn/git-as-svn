package svnserver.parser;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import svnserver.config.Config;
import svnserver.config.LocalUserDBConfig;
import svnserver.config.RepositoryConfig;
import svnserver.repository.VcsRepository;
import svnserver.repository.git.GitPushMode;
import svnserver.repository.git.GitRepository;
import svnserver.server.SvnServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Test subversion server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnTestServer implements AutoCloseable {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SvnTestServer.class);
  @NotNull
  public static final String USER_NAME = "tester";
  @NotNull
  public static final String REAL_NAME = "Test User";
  @NotNull
  public static final String EMAIL = "foo@bar.org";
  @NotNull
  public static final String PASSWORD = "passw0rd";

  @NotNull
  private final String BIND_HOST = "127.0.0.2";
  @NotNull
  private final File tempDirectory;
  @NotNull
  private final Repository repository;
  @NotNull
  private final String testBranch;
  @NotNull
  private final SvnServer server;
  private final boolean safeBranch;

  private SvnTestServer(@NotNull Repository repository, @Nullable String branch, boolean safeBranch) throws Exception {
    this.repository = repository;
    this.safeBranch = safeBranch;
    tempDirectory = TestHelper.createTempDir("git-as-svn");
    final String srcBranch = branch == null ? repository.getBranch() : branch;
    if (safeBranch) {
      testBranch = "test_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
      new Git(repository)
          .branchCreate()
          .setName(testBranch)
          .setStartPoint(srcBranch)
          .call();
    } else {
      testBranch = srcBranch;
    }

    final Config config = new Config();
    config.setPort(0);
    config.setHost(BIND_HOST);

    config.setRepository(new TestRepositoryConfig(repository, testBranch));

    config.setUserDB(new LocalUserDBConfig(new LocalUserDBConfig.UserEntry[]{
        new LocalUserDBConfig.UserEntry(USER_NAME, REAL_NAME, EMAIL, PASSWORD)
    }));

    server = new SvnServer(config);
    server.start();
    log.info("Temporary server started (url: {}, path: {}, branch: {} as {})", getUrl(), repository.getDirectory(), srcBranch, testBranch);
    log.info("Temporary directory: {}", tempDirectory);
  }

  @NotNull
  public static SvnTestServer createEmpty() throws Exception {
    final String branch = "master";
    return new SvnTestServer(TestHelper.emptyRepository(branch), branch, false);
  }

  @NotNull
  public static SvnTestServer createMasterRepository() throws Exception {
    return new SvnTestServer(new FileRepository(findGitPath()), null, true);
  }

  public SVNURL getUrl() throws SVNException {
    return SVNURL.create("svn", null, BIND_HOST, server.getPort(), "", true);
  }

  @NotNull
  public Repository getRepository() {
    return repository;
  }

  @NotNull
  public File getTempDirectory() {
    return tempDirectory;
  }

  @Override
  public void close() throws Exception {
    server.shutdown();
    if (safeBranch) {
      new Git(repository)
          .branchDelete()
          .setBranchNames(testBranch)
          .setForce(true)
          .call();
    }
    repository.close();
    deleteDirectory(tempDirectory);
  }

  private void deleteDirectory(@NotNull File file) throws IOException {
    if (file.isDirectory()) {
      final File[] files = file.listFiles();
      if (files != null) {
        for (File entry : files) {
          deleteDirectory(entry);
        }
      }
    }
    if (!file.delete()) {
      throw new FileNotFoundException("Failed to delete file: " + file);
    }
  }

  private static File findGitPath() {
    final File root = new File(".").getAbsoluteFile();
    File path = root;
    while (true) {
      final File repo = new File(path, ".git");
      if (repo.exists()) {
        return repo;
      }
      path = path.getParentFile();
      if (path == null) {
        throw new IllegalStateException("Repository not found from directiry: " + root.getAbsolutePath());
      }
    }
  }

  public ISVNAuthenticationManager getAuthenticator() {
    return new BasicAuthenticationManager(USER_NAME, PASSWORD);
  }

  private static class TestRepositoryConfig implements RepositoryConfig {
    @NotNull
    private final Repository repository;
    @NotNull
    private final String branch;

    public TestRepositoryConfig(@NotNull Repository repository, @NotNull String branch) {
      this.repository = repository;
      this.branch = branch;
    }

    @NotNull
    @Override
    public VcsRepository create() throws IOException, SVNException {
      return new GitRepository(repository, Collections.emptyList(), GitPushMode.SIMPLE, branch);
    }
  }
}
