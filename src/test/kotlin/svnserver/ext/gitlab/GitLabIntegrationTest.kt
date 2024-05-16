/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab

import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.models.*
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
    private var rootApi: GitLabApi? = null
    private var gitLabProject: Project? = null
    private var gitLabPublicProject: Project? = null

    @BeforeClass
    fun before() {
        SvnTestHelper.skipTestIfDockerUnavailable()
        var gitlabVersion = System.getenv("GITLAB_VERSION")
        if (gitlabVersion == null) {
            SvnTestHelper.skipTestIfRunningOnCI()
            gitlabVersion = "10.2.5-ce.0"
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
        rootApi = login(root, rootPassword, true)
        val gitlabUser = rootApi!!.userApi.createUser(
            User()
                .withUsername(user)
                .withName(user)
                .withEmail("git-as-svn@localhost")
                .withSkipConfirmation(true),
            userPassword,
            false)
        Assert.assertNotNull(gitlabUser)
        val group = rootApi!!.groupApi.createGroup(
            GroupParams()
                .withPath("testGroup")
                .withName("testGroup")
                .withVisibility(Visibility.PUBLIC.toValue()))
        Assert.assertNotNull(group)
        Assert.assertNotNull(rootApi!!.groupApi.addMember(group.id, gitlabUser.id, AccessLevel.DEVELOPER))
        gitLabProject = createGitLabProject(rootApi!!, group, "test", Visibility.INTERNAL, listOf("git-as-svn:master"))
        gitLabPublicProject = createGitLabProject(rootApi!!, group, "publik", Visibility.PUBLIC, listOf("git-as-svn:master"))
    }

    private fun login(username: String, password: String, sudoScope: Boolean): GitLabApi {
        return GitLabContext.login(gitlabUrl!!, username, password, sudoScope)
    }

    private fun createGitLabProject(rootAPI: GitLabApi, group: Group, name: String, visibility: Visibility, topics: List<String>): Project {
        return rootAPI.projectApi.createProject(Project()
            .withName(name)
            .withNamespaceId(group.id)
            .withVisibility(visibility)
            .withTopics(topics)
            .withTagList(topics))
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
        createServer(rootApi!!, null).use { server -> server.openSvnRepository(login, password).latestRevision }
    }

    private fun createServer(api: GitLabApi, mappingConfigCreator: Function<Path, RepositoryMappingConfig>?): SvnTestServer {
        val gitLabConfig = GitLabConfig(gitlabUrl!!, GitLabToken(api.tokenType, api.authToken))
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
        createServer(rootApi!!) {
            dir: Path? -> GitLabMappingConfig(dir!!, GitCreateMode.EMPTY)
        }.use {
            openSvnRepository(it, gitLabProject!!, user, userPassword).latestRevision
        }
    }

    private fun openSvnRepository(server: SvnTestServer, gitLabProject: Project, username: String, password: String): SVNRepository {
        return SvnTestServer.openSvnRepository(server.getUrl(false).appendPath(gitLabProject.pathWithNamespace + "/master", false), username, password)
    }

    @Test
    fun testLfs() {
        val storage = GitLabConfig.createLfsStorage(gitlabUrl!!, gitLabProject!!.pathWithNamespace, root, rootPassword, null)
        val user = User.create(root, root, root, root, UserType.GitLab, LfsCredentials(root, rootPassword))
        LfsLocalStorageTest.checkLfs(storage, user)
        LfsLocalStorageTest.checkLfs(storage, user)
        LfsLocalStorageTest.checkLocks(storage, user)
    }

    @Test
    fun gitlabMappingForAnonymous() {
        createServer(rootApi!!) {
            dir: Path? -> GitLabMappingConfig(dir!!, GitCreateMode.EMPTY)
        }.use {
            openSvnRepository(it, gitLabPublicProject!!, "nobody", "nopassword").latestRevision
        }
    }

    /**
     * Test for #119.
     */
    @Ignore
    @Test
    fun gitlabMappingAsUser() {
        login(user, userPassword, false).use {
            createServer(it) {
                    dir: Path? -> GitLabMappingConfig(dir!!, GitCreateMode.EMPTY)
            }.use {
                openSvnRepository(it, gitLabProject!!, root, rootPassword).latestRevision
            }
        }
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
        private const val rootPassword = "P6122kAYf6Wc"
        private const val user = "git-as-svn"
        private const val userPassword = "R4BS2xN6ZsO4"
    }
}
