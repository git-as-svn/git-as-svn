/*
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
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.tmatesoft.sqljet.core.internal.SqlJetPagerJournalMode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCompression;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import svnserver.config.*;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;
import svnserver.ext.gitlfs.storage.memory.LfsMemoryStorage;
import svnserver.ext.web.config.WebServerConfig;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.VcsAccess;
import svnserver.repository.git.EmptyDirsSupport;
import svnserver.repository.git.GitRepository;
import svnserver.repository.git.push.GitPushEmbedded;
import svnserver.server.SvnServer;
import svnserver.tester.SvnTester;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * Test subversion server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnTestServer implements SvnTester {
  @NotNull
  public static final String USER_NAME_NO_MAIL = "nomail";
  @NotNull
  public static final String PASSWORD = "passw0rd";
  @NotNull
  public static final String USER_NAME = "tester";
  @NotNull
  private static final Logger log = TestHelper.logger;
  @NotNull
  private static final String REAL_NAME = "Test User";
  @NotNull
  private static final String EMAIL = "foo@bar.org";
  @NotNull
  private static final String TEST_BRANCH_PREFIX = "test_";

  @NotNull
  private final String BIND_HOST = "127.0.0.2";
  @NotNull
  private final Path tempDirectory;
  @NotNull
  private final Repository repository;
  @NotNull
  private final String testBranch;
  @NotNull
  private final String prefix;
  @NotNull
  private final SvnServer server;
  @NotNull
  private final List<SvnOperationFactory> svnFactories = new ArrayList<>();

  private final boolean safeBranch;

  private SvnTestServer(@NotNull Repository repository,
                        @Nullable String branch,
                        @NotNull String prefix,
                        boolean safeBranch,
                        @Nullable UserDBConfig userDBConfig,
                        @Nullable Function<Path, RepositoryMappingConfig> mappingConfigCreator,
                        boolean anonymousRead,
                        @NotNull LfsMode lfsMode,
                        @NotNull EmptyDirsSupport emptyDirs,
                        @NotNull SharedConfig... shared) throws Exception {
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

    this.prefix = prefix + "/" + testBranch;

    final Config config = new Config(BIND_HOST, 0);
    config.setCompressionLevel(SVNDeltaCompression.None);
    config.setCacheConfig(new MemoryCacheConfig());

    switch (lfsMode) {
      case Local: {
        config.getShared().add(new WebServerConfig(0));
        config.getShared().add(new LocalLfsConfig(tempDirectory.resolve("lfs").toString(), false));
        break;
      }
      case Memory: {
        config.getShared().add(context -> context.add(LfsStorageFactory.class, localContext -> new LfsMemoryStorage()));
        break;
      }
    }

    if (mappingConfigCreator != null) {
      config.setRepositoryMapping(mappingConfigCreator.apply(tempDirectory));
    } else {
      config.setRepositoryMapping(new TestRepositoryConfig(repository, testBranch, prefix, anonymousRead, emptyDirs));
    }

    if (userDBConfig != null) {
      config.setUserDB(userDBConfig);
    } else {
      config.setUserDB(new LocalUserDBConfig(new LocalUserDBConfig.UserEntry[]{
          new LocalUserDBConfig.UserEntry(USER_NAME, REAL_NAME, EMAIL, PASSWORD),
          new LocalUserDBConfig.UserEntry(USER_NAME_NO_MAIL, REAL_NAME, null, PASSWORD),
      }));
    }

    Collections.addAll(config.getShared(), shared);

    server = new SvnServer(tempDirectory, config);
    server.start();
    log.info("Temporary server started (url: {}, path: {}, branch: {} as {})", getUrl(), repository.getDirectory(), srcBranch, testBranch);
    log.info("Temporary directory: {}", tempDirectory);
  }

  private void cleanupBranches(@NotNull Repository repository) throws IOException {
    final List<String> branches = new ArrayList<>();
    for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS + TEST_BRANCH_PREFIX)) {
      branches.add(ref.getName().substring(Constants.R_HEADS.length()));
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

  @Override
  @NotNull
  public SVNURL getUrl() throws SVNException {
    return getUrl(true);
  }

  @NotNull
  public SVNURL getUrl(boolean withPrefix) throws SVNException {
    return SVNURL.create("svn", null, BIND_HOST, server.getPort(), withPrefix ? prefix : "", true);
  }

  @NotNull
  public SVNRepository openSvnRepository() throws SVNException {
    return openSvnRepository(USER_NAME, PASSWORD);
  }

  @NotNull
  public SVNRepository openSvnRepository(@NotNull String username, @NotNull String password) throws SVNException {
    return openSvnRepository(getUrl(), username, password);
  }

  @NotNull
  public static SVNRepository openSvnRepository(@NotNull SVNURL url, @NotNull String username, @NotNull String password) throws SVNException {
    final SVNRepository repo = SVNRepositoryFactory.create(url);
    repo.setAuthenticationManager(BasicAuthenticationManager.newInstance(username, password.toCharArray()));
    return repo;
  }

  @NotNull
  public static SvnTestServer createEmpty() throws Exception {
    return createEmpty(null, false, LfsMode.Memory, EmptyDirsSupport.Disabled);
  }

  @NotNull
  public static SvnTestServer createEmpty(@Nullable UserDBConfig userDBConfig, boolean anonymousRead, @NotNull LfsMode lfsMode, @NotNull EmptyDirsSupport emptyDirs, @NotNull SharedConfig... shared) throws Exception {
    return createEmpty(userDBConfig, null, anonymousRead, lfsMode, emptyDirs, shared);
  }

  @NotNull
  public static SvnTestServer createEmpty(@Nullable UserDBConfig userDBConfig, @Nullable Function<Path, RepositoryMappingConfig> mappingConfigCreator, boolean anonymousRead, @NotNull LfsMode lfsMode, @NotNull EmptyDirsSupport emptyDirs, @NotNull SharedConfig... shared) throws Exception {
    return new SvnTestServer(TestHelper.emptyRepository(), Constants.MASTER, "", false, userDBConfig, mappingConfigCreator, anonymousRead, lfsMode, emptyDirs, shared);
  }

  @NotNull
  public static SvnTestServer createEmpty(@Nullable UserDBConfig userDBConfig, @Nullable Function<Path, RepositoryMappingConfig> mappingConfigCreator, boolean anonymousRead, @NotNull LfsMode lfsMode, @NotNull SharedConfig... shared) throws Exception {
    return createEmpty(userDBConfig, mappingConfigCreator, anonymousRead, lfsMode, EmptyDirsSupport.Disabled, shared);
  }

  @NotNull
  public static SvnTestServer createEmpty(@Nullable UserDBConfig userDBConfig, boolean anonymousRead, @NotNull LfsMode lfsMode, @NotNull SharedConfig... shared) throws Exception {
    return createEmpty(userDBConfig, null, anonymousRead, lfsMode, EmptyDirsSupport.Disabled, shared);
  }

  @NotNull
  public static SvnTestServer createEmpty(@NotNull EmptyDirsSupport emptyDirs) throws Exception {
    return createEmpty(null, false, LfsMode.Memory, emptyDirs);
  }

  @NotNull
  public static SvnTestServer createEmpty(@Nullable UserDBConfig userDBConfig, boolean anonymousRead, @NotNull SharedConfig... shared) throws Exception {
    return createEmpty(userDBConfig, null, anonymousRead, LfsMode.Memory, EmptyDirsSupport.Disabled, shared);
  }

  @NotNull
  public static SvnTestServer createMasterRepository() throws Exception {
    return new SvnTestServer(new FileRepository(TestHelper.findGitPath().toFile()), null, "", true, null, null, true, LfsMode.Memory, EmptyDirsSupport.Disabled);
  }

  @NotNull
  public Repository getRepository() {
    return repository;
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
    for (SvnOperationFactory factory : svnFactories) {
      factory.dispose();
    }
    svnFactories.clear();
    repository.close();
    TestHelper.deleteDirectory(tempDirectory);
  }

  public void shutdown(int millis) throws Exception {
    server.shutdown(millis);
  }

  @NotNull
  public SvnOperationFactory createOperationFactory() {
    return createOperationFactory(USER_NAME, PASSWORD);
  }

  @NotNull
  private SvnOperationFactory createOperationFactory(@NotNull String username, @NotNull String password) {
    final SVNWCContext wcContext = new SVNWCContext(new DefaultSVNOptions(getTempDirectory().toFile(), true), null);
    wcContext.setSqliteTemporaryDbInMemory(true);
    wcContext.setSqliteJournalMode(SqlJetPagerJournalMode.MEMORY);

    final SvnOperationFactory factory = new SvnOperationFactory(wcContext);
    factory.setAuthenticationManager(BasicAuthenticationManager.newInstance(username, password.toCharArray()));
    svnFactories.add(factory);
    return factory;
  }

  @NotNull
  public Path getTempDirectory() {
    return tempDirectory;
  }

  public void startShutdown() throws IOException {
    server.startShutdown();
  }

  @NotNull
  public SharedContext getContext() {
    return server.getSharedContext();
  }

  public enum LfsMode {
    None,
    Memory,
    Local
  }

  private static final class TestRepositoryConfig implements RepositoryMappingConfig {
    @NotNull
    private final Repository git;
    @NotNull
    private final String branch;
    @NotNull
    private final String prefix;
    private final boolean anonymousRead;
    private final EmptyDirsSupport emptyDirs;

    private TestRepositoryConfig(@NotNull Repository git, @NotNull String branch, @NotNull String prefix, boolean anonymousRead, @NotNull EmptyDirsSupport emptyDirs) {
      this.git = git;
      this.branch = branch;
      this.prefix = prefix;
      this.anonymousRead = anonymousRead;
      this.emptyDirs = emptyDirs;
    }

    @NotNull
    @Override
    public RepositoryMapping<GitRepository> create(@NotNull SharedContext context, boolean canUseParallelIndexing) throws IOException {
      final LocalContext local = new LocalContext(context, "test");
      local.add(VcsAccess.class, anonymousRead ? VcsAccessEveryone.instance : VcsAccessNoAnonymous.instance);

      final GitRepository repository = GitRepositoryConfig.createRepository(
          local,
          LfsStorageFactory.tryCreateStorage(local),
          git,
          new GitPushEmbedded(local, null, false),
          Collections.singleton(branch),
          true,
          emptyDirs
      );

      return () -> new TreeMap<>(Collections.singletonMap(prefix, repository));
    }
  }
}
