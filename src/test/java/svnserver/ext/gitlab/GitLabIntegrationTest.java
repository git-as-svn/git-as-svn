/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.Wait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import svnserver.SvnTestServer;
import svnserver.config.RepositoryMappingConfig;
import svnserver.ext.gitlab.auth.GitLabUserDBConfig;
import svnserver.ext.gitlab.config.GitLabConfig;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.ext.gitlab.mapping.GitLabMappingConfig;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GitLabIntegrationTest {

  private static final int gitlabPort = 80;

  @NotNull
  private static final String root = "root";
  @NotNull
  private static final String rootPassword = "12345678";

  private GenericContainer<?> gitlab;
  private String gitlabUrl;
  private String rootAccessToken;

  @BeforeClass
  void before() throws Exception {
    String gitlabVersion = System.getenv("GITLAB_VERSION");
    if (gitlabVersion == null)
      gitlabVersion = "9.3.3-ce.0";

    gitlab = new GenericContainer<>("gitlab/gitlab-ce:" + gitlabVersion)
        .withEnv("GITLAB_ROOT_PASSWORD", rootPassword)
        .withExposedPorts(gitlabPort)
        .waitingFor(Wait.forHttp("")
            .withStartupTimeout(Duration.of(10, ChronoUnit.MINUTES))
        );
    gitlab.start();

    gitlabUrl = "http://" + gitlab.getContainerIpAddress() + ":" + gitlab.getMappedPort(gitlabPort);
    rootAccessToken = GitLabContext.obtainAccessToken(gitlabUrl, root, rootPassword);
  }

  @AfterClass
  void after() throws Exception {
    if (gitlab != null) {
      gitlab.stop();
      gitlab = null;
    }
  }

  @Test
  void validUser() throws Exception {
    checkUser(root, rootPassword);
  }

  private void checkUser(@NotNull String login, @NotNull String password) throws Exception {
    try (SvnTestServer server = createServer(rootAccessToken, null)) {
      server.openSvnRepository(login, password).getLatestRevision();
    }
  }

  @NotNull
  private SvnTestServer createServer(@NotNull String accessToken, @Nullable Function<File, RepositoryMappingConfig> mappingConfigCreator) throws Exception {
    // TODO: use private token when GitLab adds API to manage them. https://gitlab.com/gitlab-org/gitlab-ce/issues/27954
    final GitLabConfig gitLabConfig = new GitLabConfig(gitlabUrl, accessToken, TokenType.ACCESS_TOKEN);
    return SvnTestServer.createEmpty(new GitLabUserDBConfig(), mappingConfigCreator, false, gitLabConfig);
  }

  @Test(expectedExceptions = SVNAuthenticationException.class)
  void invalidPassword() throws Throwable {
    checkUser(root, "wrongpassword");
  }

  @Test(expectedExceptions = SVNAuthenticationException.class)
  void invalidUser() throws Throwable {
    checkUser("wronguser", rootPassword);
  }

  @Test
  void gitlabMappingAsRoot() throws Exception {
    try (SvnTestServer server = createServer(rootAccessToken, GitLabMappingConfig::create)) {
      // TODO: check repo list
    }
  }

  /**
   * Test for #119.
   */
  @Test
  void gitlabMappingAsUser() throws Exception {
    final GitlabAPI api = GitlabAPI.connect(gitlabUrl, rootAccessToken, TokenType.ACCESS_TOKEN);
    api.createUser("git-as-svn@localhost", "git-as-svn", "git-as-svn", "git-as-svn", null, null, null, null, null, null, null, null, false, null, true);
    final String userAccessToken = GitLabContext.obtainAccessToken(gitlabUrl, "git-as-svn", "git-as-svn");

    try (SvnTestServer server = createServer(userAccessToken, GitLabMappingConfig::create)) {
      // TODO: check repo list
    }
  }
}
