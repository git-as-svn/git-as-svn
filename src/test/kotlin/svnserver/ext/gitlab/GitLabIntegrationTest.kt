/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab

import org.gitlab.api.GitlabAPI
import org.gitlab.api.http.Query
import org.gitlab.api.models.*
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy
import org.testng.Assert
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Ignore
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNAuthenticationException
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.KFixedHostPortGenericContainer
import svnserver.SvnTestHelper
import svnserver.SvnTestServer
import svnserver.UserType
import svnserver.auth.User
import svnserver.auth.User.LfsCredentials
import svnserver.config.RepositoryMappingConfig
import svnserver.ext.gitlab.auth.GitLabUserDBConfig
import svnserver.ext.gitlab.config.GitLabConfig
import svnserver.ext.gitlab.config.GitLabContext
import svnserver.ext.gitlab.config.GitLabToken
import svnserver.ext.gitlab.mapping.GitLabMappingConfig
import svnserver.ext.gitlfs.storage.local.LfsLocalStorageTest
import svnserver.ext.web.config.WebServerConfig
import svnserver.repository.git.GitCreateMode
import java.nio.file.Path
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.function.Function

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GitLabIntegrationTest {
    private var gitlab: GenericContainer<*>? = null
    private var gitlabUrl: String? = null
    private var rootToken: GitLabToken? = null
    private var gitlabProject: GitlabProject? = null
    private var gitlabPublicProject: GitlabProject? = null

    @BeforeClass
    fun before() {
        SvnTestHelper.skipTestIfDockerUnavailable()
        var gitlabVersion = System.getenv("GITLAB_VERSION")
        if (gitlabVersion == null) {
            SvnTestHelper.skipTestIfRunningOnCI()
            gitlabVersion = "9.3.3-ce.0"
        }
        val hostPort = 9999
        // containerPort is supposed to be 80, but GitLab binds to port from external_url
        // See https://stackoverflow.com/questions/39351563/gitlab-docker-not-working-if-external-url-is-set
        val containerPort = 9999
        val hostname = DockerClientFactory.instance().dockerHostIpAddress()
        gitlabUrl = String.format("http://%s:%s", hostname, hostPort)
        gitlab = KFixedHostPortGenericContainer("gitlab/gitlab-ce:$gitlabVersion") // We have a chicken-and-egg problem here. In order to set external_url, we need to know container address,
            // but we do not know container address until container is started.
            // So, for now use fixed port :(
            .withFixedExposedPort(hostPort, containerPort) // This is kinda stupid that we need to do withExposedPorts even when we have withFixedExposedPort
            .withExposedPorts(containerPort)
            .withEnv("GITLAB_OMNIBUS_CONFIG", String.format("external_url '%s'", gitlabUrl))
            .withEnv("GITLAB_ROOT_PASSWORD", rootPassword)
            .waitingFor(
                WaitForChefComplete()
                    .withStartupTimeout(Duration.of(10, ChronoUnit.MINUTES))
            )
        gitlab!!.start()
        rootToken = createToken(root, rootPassword, true)
        val rootAPI = GitLabContext.connect(gitlabUrl!!, rootToken!!)
        val createUserRequest = CreateUserRequest(user, user, "git-as-svn@localhost")
            .setPassword(userPassword)
            .setSkipConfirmation(true)
        val gitlabUser = rootAPI.createUser(createUserRequest)
        Assert.assertNotNull(gitlabUser)
        val group = rootAPI.createGroup(CreateGroupRequest("testGroup").setVisibility(GitlabVisibility.PUBLIC), null)
        Assert.assertNotNull(group)
        Assert.assertNotNull(rootAPI.addGroupMember(group.id, gitlabUser.id, GitlabAccessLevel.Developer))
        gitlabProject = createGitlabProject(rootAPI, group, "test", GitlabVisibility.INTERNAL, setOf("git-as-svn:master"))
        gitlabPublicProject = createGitlabProject(rootAPI, group, "publik", GitlabVisibility.PUBLIC, setOf("git-as-svn:master"))
    }

    private fun createToken(username: String, password: String, sudoScope: Boolean): GitLabToken {
        return GitLabContext.obtainAccessToken(gitlabUrl!!, username, password, sudoScope)
    }

    private fun createGitlabProject(rootAPI: GitlabAPI, group: GitlabGroup, name: String, visibility: GitlabVisibility, tags: Set<String>): GitlabProject {
        // java-gitlab-api doesn't handle tag_list, so we have to do this manually
        val query = Query()
            .append("name", name)
            .appendIf("namespace_id", group.id)
            .appendIf("visibility", visibility.toString())
            .appendIf("tag_list", java.lang.String.join(",", tags))
        val tailUrl = GitlabProject.URL + query.toString()
        return rootAPI.dispatch().to(tailUrl, GitlabProject::class.java)
    }

    @AfterClass
    fun after() {
        if (gitlab != null) {
            gitlab!!.stop()
            gitlab = null
        }
    }

    @Test
    fun validUser() {
        checkUser(root, rootPassword)
    }

    private fun checkUser(login: String, password: String) {
        createServer(rootToken!!, null).use { server -> server.openSvnRepository(login, password).latestRevision }
    }

    private fun createServer(token: GitLabToken, mappingConfigCreator: Function<Path, RepositoryMappingConfig>?): SvnTestServer {
        val gitLabConfig = GitLabConfig(gitlabUrl!!, token)
        return SvnTestServer.createEmpty(GitLabUserDBConfig(), mappingConfigCreator, false, SvnTestServer.LfsMode.None, gitLabConfig, WebServerConfig())
    }

    @Test
    fun invalidPassword() {
        Assert.expectThrows(SVNAuthenticationException::class.java) { checkUser(root, "wrongpassword") }
    }

    @Test
    fun invalidUser() {
        Assert.expectThrows(SVNAuthenticationException::class.java) { checkUser("wronguser", rootPassword) }
    }

    @Test
    fun gitlabMappingAsRoot() {
        createServer(rootToken!!) { dir: Path? -> GitLabMappingConfig(dir!!, GitCreateMode.EMPTY) }.use { server -> openSvnRepository(server, gitlabProject!!, user, userPassword).latestRevision }
    }

    private fun openSvnRepository(server: SvnTestServer, gitlabProject: GitlabProject, username: String, password: String): SVNRepository {
        return SvnTestServer.openSvnRepository(server.getUrl(false).appendPath(gitlabProject.pathWithNamespace + "/master", false), username, password)
    }

    @Test
    fun testLfs() {
        val storage = GitLabConfig.createLfsStorage(gitlabUrl!!, gitlabProject!!.pathWithNamespace, root, rootPassword, null)
        val user = User.create(root, root, root, root, UserType.GitLab, LfsCredentials(root, rootPassword))
        LfsLocalStorageTest.checkLfs(storage, user)
        LfsLocalStorageTest.checkLfs(storage, user)
        LfsLocalStorageTest.checkLocks(storage, user)
    }

    @Test
    fun gitlabMappingForAnonymous() {
        createServer(rootToken!!) { dir: Path? -> GitLabMappingConfig(dir!!, GitCreateMode.EMPTY) }.use { server -> openSvnRepository(server, gitlabPublicProject!!, "nobody", "nopassword").latestRevision }
    }

    /**
     * Test for #119.
     */
    @Ignore
    @Test
    fun gitlabMappingAsUser() {
        val userToken = createToken(user, userPassword, false)
        createServer(userToken) { dir: Path? -> GitLabMappingConfig(dir!!, GitCreateMode.EMPTY) }.use { server -> openSvnRepository(server, gitlabProject!!, root, rootPassword).latestRevision }
    }

    private class WaitForChefComplete : AbstractWaitStrategy() {
        override fun waitUntilReady() {
            Unreliables.retryUntilSuccess(startupTimeout.seconds.toInt(), TimeUnit.SECONDS) {
                rateLimiter.doWhenReady {
                    val execResult = waitStrategyTarget.execInContainer("grep", "-R", "-E", "(Chef|Cinc).* Run complete", "/var/log/gitlab/reconfigure/")
                    if (execResult.exitCode != 0) {
                        throw RuntimeException("Not ready")
                    }
                }
                true
            }
        }
    }

    companion object {
        private const val root = "root"
        private const val rootPassword = "12345678"
        private const val user = "git-as-svn"
        private const val userPassword = "git-as-svn"
    }
}
