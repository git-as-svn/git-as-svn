package svnserver.parser;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
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
import svnserver.server.SvnServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
  private final FileRepository repository;
  @NotNull
  private final String testBranch;
  @NotNull
  private final SvnServer server;

  public SvnTestServer(@Nullable String branch) throws Exception {
    repository = new FileRepository(findGitPath());
    testBranch = "test_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);

    tempDirectory = File.createTempFile("git-as-svn", "");
    tempDirectory.delete();
    tempDirectory.mkdir();

    final String srcBranch = branch == null ? repository.getBranch() : branch;
    new Git(repository)
        .branchCreate()
        .setName(testBranch)
        .setStartPoint(srcBranch)
        .call();

    final Config config = new Config();
    config.setPort(0);
    config.setHost(BIND_HOST);

    final RepositoryConfig configRepository = config.getRepository();
    configRepository.setPath(repository.getDirectory().getPath());
    configRepository.setBranch(testBranch);

    config.setUserDB(new LocalUserDBConfig(new LocalUserDBConfig.UserEntry[]{
        new LocalUserDBConfig.UserEntry(USER_NAME, REAL_NAME, EMAIL, PASSWORD)
    }));

    server = new SvnServer(config);
    server.start();
    log.info("Temporary server started (url: {}, path: {}, branch: {} as {})", getUrl(), repository.getDirectory(), srcBranch, testBranch);
    log.info("Temporary directory: {}", tempDirectory);
  }

  public SVNURL getUrl() throws SVNException {
    return SVNURL.create("svn", null, BIND_HOST, server.getPort(), "", true);
  }

  @NotNull
  public File getTempDirectory() {
    return tempDirectory;
  }

  @Override
  public void close() throws Exception {
    server.shutdown();
    new Git(repository)
        .branchDelete()
        .setBranchNames(testBranch)
        .setForce(true)
        .call();
    repository.close();
    deleteDirectory(tempDirectory);
  }

  private void deleteDirectory(@NotNull File file) throws IOException {
    if (file.isDirectory()) {
      final File[] files = file.listFiles();
      if (files!=null){
      for (File entry : files) {
        deleteDirectory(entry);
      }}
    }
    if (!file.delete()) {
      throw new FileNotFoundException("Failed to delete file: " + file);
    }
  }

  private File findGitPath() {
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
}
