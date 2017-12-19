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
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
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

  @NotNull
  private static final GenericContainer<?> gitlab;

  static {
    String gitlabVersion = System.getenv("GITLAB_VERSION");
    if (gitlabVersion == null)
      gitlabVersion = "10.2.5-ce.0";

    gitlab = new GenericContainer<>("gitlab/gitlab-ce:" + gitlabVersion)
        .withEnv("GITLAB_ROOT_PASSWORD", rootPassword)
        .withExposedPorts(gitlabPort)
        .waitingFor(Wait.forHttp("")
            .withStartupTimeout(Duration.of(10, ChronoUnit.MINUTES))
        );
  }

  @BeforeTest
  void before() {
    gitlab.start();
  }

  @AfterTest
  void after() {
    gitlab.stop();
  }

  @Test
  public void gitlabAuthentication() throws Exception {
    final String gitlabUrl = "http://" + gitlab.getContainerIpAddress() + ":" + gitlab.getMappedPort(gitlabPort);
    final String token = GitLabContext.obtainToken(gitlabUrl, root, rootPassword);
    final GitLabConfig gitlabConfig = new GitLabConfig(gitlabUrl, token);

    try (SvnTestServer server = SvnTestServer.createEmpty(new GitLabUserDBConfig(), false, gitlabConfig)) {
      server.openSvnRepository(root, rootPassword).getLatestRevision();
    }
  }
}
