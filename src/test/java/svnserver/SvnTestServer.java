/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import svnserver.config.Config;
import svnserver.config.LocalUserDBConfig;
import svnserver.config.RepositoryConfig;
import svnserver.config.UserDBConfig;
import svnserver.repository.VcsRepository;
import svnserver.repository.git.GitPushMode;
import svnserver.repository.git.GitRepository;
import svnserver.repository.locks.DumbLockManager;
import svnserver.server.SvnServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
  private static final String TEST_BRANCH_PREFIX = "test_";

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

  private SvnTestServer(@NotNull Repository repository, @Nullable String branch, boolean safeBranch, @Nullable UserDBConfig userDBConfig) throws Exception {
    SVNFileUtil.setSleepForTimestamp(false);
    this.repository = repository;
    this.safeBranch = safeBranch;
    tempDirectory = TestHelper.createTempDir("git-as-svn");
    final String srcBranch = branch == null ? repository.getBranch() : branch;
    if (safeBranch) {
      cleanupBranches(repository);
      testBranch = TEST_BRANCH_PREFIX + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
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
    if (userDBConfig != null) {
      config.setUserDB(userDBConfig);
    } else {
      config.setUserDB(new LocalUserDBConfig(new LocalUserDBConfig.UserEntry[]{
          new LocalUserDBConfig.UserEntry(USER_NAME, REAL_NAME, EMAIL, PASSWORD)
      }));
    }
    server = new SvnServer(config);
    server.start();
    log.info("Temporary server started (url: {}, path: {}, branch: {} as {})", getUrl(), repository.getDirectory(), srcBranch, testBranch);
    log.info("Temporary directory: {}", tempDirectory);
  }

  private void cleanupBranches(Repository repository) {
    final List<String> branches = new ArrayList<>();
    for (String ref : repository.getAllRefs().keySet()) {
      if (ref.startsWith(Constants.R_HEADS + TEST_BRANCH_PREFIX)) {
        branches.add(ref.substring(Constants.R_HEADS.length()));
      }
    }
    if (!branches.isEmpty()) {
      for (String branch : branches) {
        log.info("Cleanup branch: {}", branch);
        try {
          new Git(repository)
              .branchDelete()
              .setBranchNames(branch)
              .setForce(true)
              .call();
        } catch (GitAPIException e) {
          log.error("Cleanup branch: " + branch, e);
        }
      }
    }
  }

  @NotNull
  public static SvnTestServer createEmpty() throws Exception {
    final String branch = "master";
    return new SvnTestServer(TestHelper.emptyRepository(), branch, false, null);
  }

  @NotNull
  public static SvnTestServer createEmpty(@Nullable UserDBConfig userDBConfig) throws Exception {
    final String branch = "master";
    return new SvnTestServer(TestHelper.emptyRepository(), branch, false, userDBConfig);
  }

  @NotNull
  public static SvnTestServer createMasterRepository() throws Exception {
    return new SvnTestServer(new FileRepository(findGitPath()), null, true, null);
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

  public void shutdown(int millis) throws IOException, InterruptedException {
    server.shutdown(millis);
  }

  @Override
  public void close() throws Exception {
    shutdown(0);
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

  @NotNull
  public SvnOperationFactory createOperationFactory() {
    return createOperationFactory(USER_NAME, PASSWORD);
  }

  @NotNull
  public SvnOperationFactory createOperationFactory(@NotNull String userName, @NotNull String password) {
    final SvnOperationFactory factory = new SvnOperationFactory();
    factory.setOptions(new DefaultSVNOptions(getTempDirectory(), true));
    factory.setAuthenticationManager(new BasicAuthenticationManager(userName, password));
    return factory;
  }

  @NotNull
  public SVNRepository openSvnRepository() throws SVNException {
    return openSvnRepository(USER_NAME, PASSWORD);
  }

  @NotNull
  public SVNRepository openSvnRepository(@NotNull String userName, @NotNull String password) throws SVNException {
    final SVNRepository repo = SVNRepositoryFactory.create(getUrl());
    repo.setAuthenticationManager(new BasicAuthenticationManager(userName, password));
    return repo;
  }

  public void startShutdown() throws IOException {
    server.startShutdown();
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
      return new GitRepository(
          repository,
          Collections.emptyList(),
          GitPushMode.SIMPLE,
          branch,
          true,
          new DumbLockManager(true)
      );
    }
  }
}
