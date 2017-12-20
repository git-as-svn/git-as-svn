/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.Wait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import svnserver.SvnTestServer;
import svnserver.ext.gitlab.auth.GitLabUserDBConfig;
import svnserver.ext.gitlab.config.GitLabConfig;
import svnserver.ext.gitlab.config.GitLabContext;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

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
  private String rootToken;
  private SvnTestServer server;

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
    rootToken = GitLabContext.obtainToken(gitlabUrl, root, rootPassword);

    final GitLabConfig gitlabConfig = new GitLabConfig(gitlabUrl, rootToken);
    server = SvnTestServer.createEmpty(new GitLabUserDBConfig(), false, gitlabConfig);
  }

  @AfterClass
  void after() throws Exception {
    if (server != null)
      server.close();
    if (gitlab != null)
      gitlab.stop();
  }

  @Test
  void validUser() throws Exception {
    checkUser(root, rootPassword);
  }

  private void checkUser(@NotNull String login, @NotNull String password) throws Exception {
    server.openSvnRepository(login, password).getLatestRevision();
  }

  @Test(expectedExceptions = SVNAuthenticationException.class)
  void invalidPassword() throws Throwable {
    checkUser(root, "wrongpassword");
  }

  @Test(expectedExceptions = SVNAuthenticationException.class)
  void invalidUser() throws Throwable {
    checkUser("wronguser", rootPassword);
  }
}
