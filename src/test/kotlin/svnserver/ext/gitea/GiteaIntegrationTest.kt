/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea

import io.gitea.ApiClient
import io.gitea.api.RepositoryApi
import io.gitea.api.UserApi
import io.gitea.auth.ApiKeyAuth
import io.gitea.model.AccessTokenName
import io.gitea.model.AddCollaboratorOption
import io.gitea.model.CreateRepoOption
import io.gitea.model.Repository
import org.eclipse.jetty.util.ArrayUtil
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testng.Assert
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNAuthenticationException
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.*
import svnserver.auth.User
import svnserver.auth.User.LfsCredentials
import svnserver.config.RepositoryMappingConfig
import svnserver.ext.gitea.auth.GiteaUserDBConfig
import svnserver.ext.gitea.config.GiteaConfig
import svnserver.ext.gitea.config.GiteaContext
import svnserver.ext.gitea.config.GiteaToken
import svnserver.ext.gitea.mapping.GiteaMappingConfig
import svnserver.ext.gitlfs.storage.local.LfsLocalStorageTest
import svnserver.repository.git.GitCreateMode
import java.io.IOException
import java.nio.file.Path
import java.util.function.Function

/**
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
class GiteaIntegrationTest {
    private var gitea: GenericContainer<*>? = null
    private var giteaUrl: String? = null
    private var giteaApiUrl: String? = null
    private var administratorToken: GiteaToken? = null
    private var testPublicRepository: Repository? = null
    private var testPrivateRepository: Repository? = null

    @BeforeClass
    @Throws(Exception::class)
    fun before() {
        SvnTestHelper.skipTestIfDockerUnavailable()
        var giteaVersion = System.getenv("GITEA_VERSION")
        if (giteaVersion == null) {
            SvnTestHelper.skipTestIfRunningOnCI()
            giteaVersion = "latest"
        }
        val hostPort = 9999
        val containerPort = 3000
        val hostname = DockerClientFactory.instance().dockerHostIpAddress()
        giteaUrl = String.format("http://%s:%s", hostname, hostPort)
        giteaApiUrl = "$giteaUrl/api/v1"
        gitea = KFixedHostPortGenericContainer("gitea/gitea:$giteaVersion")
            .withFixedExposedPort(hostPort, containerPort)
            .withExposedPorts(containerPort)
            .withEnv("ROOT_URL", giteaUrl)
            .withEnv("INSTALL_LOCK", "true")
            .withEnv("SECRET_KEY", "CmjF5WBUNZytE2C80JuogljLs5enS0zSTlikbP2HyG8IUy15UjkLNvTNsyYW7wN")
            .withEnv("RUN_MODE", "prod")
            .withEnv("LFS_START_SERVER", "true")
            .waitingFor(Wait.forHttp("/user/login"))
            .withLogConsumer(Slf4jLogConsumer(log))
        gitea!!.start()
        doCreateUser(administrator, "administrator@example.com", administratorPassword, "--admin")
        val apiClient = ApiClient()
        apiClient.basePath = giteaApiUrl
        apiClient.setUsername(administrator)
        apiClient.setPassword(administratorPassword)
        val userApi = UserApi(apiClient)
        val accessTokenName = AccessTokenName()
        accessTokenName.name = "integration-test"
        val token = userApi.userCreateToken(administrator, accessTokenName)
        administratorToken = GiteaToken(token.sha1)

        // Switch to the GiteaContext approach
        // CreateTestUser
        val testUser = createUser(user, userPassword)
        Assert.assertNotNull(testUser)
        val collaboratorUser = createUser(collaborator, collaboratorPassword)
        Assert.assertNotNull(collaboratorUser)

        // Create a repository for the test user
        testPublicRepository = createRepository(user, "public-user-repo", "Public User Repository", false, true)
        Assert.assertNotNull(testPublicRepository)
        testPrivateRepository = createRepository(user, "private-user-repo", "Private User Repository", true, true)
        Assert.assertNotNull(testPrivateRepository)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun doCreateUser(username: String, email: String, password: String, vararg extraArgs: String) {
        val args = ArrayUtil.add(
            arrayOf("--username", username, "--password", password, "--email", email, "--must-change-password=false", "-c", "/data/gitea/conf/app.ini"),
            extraArgs
        )
        var execResult = gitea!!.execInContainer(*ArrayUtil.add(arrayOf("gitea", "admin", "user", "create"), args))
        if (execResult.exitCode == 3) {
            execResult = gitea!!.execInContainer(*ArrayUtil.add(arrayOf("gitea", "admin", "create-user"), args))
        }
        Assert.assertEquals(execResult.exitCode, 0)
    }

    @Throws(Exception::class)
    private fun createUser(username: String, password: String): io.gitea.model.User {
        return createUser(username, "$username@example.com", password)
    }

    @Throws(Exception::class)
    private fun createRepository(username: String, name: String, description: String, _private: Boolean?, autoInit: Boolean?): Repository {
        val apiClient = sudo(GiteaContext.connect(giteaApiUrl!!, administratorToken!!), username)
        val repositoryApi = RepositoryApi(apiClient)
        val repoOption = CreateRepoOption()
        repoOption.name = name
        repoOption.description = description
        repoOption.isPrivate = _private
        repoOption.isAutoInit = autoInit
        repoOption.readme = "Default"
        return repositoryApi.createCurrentUserRepo(repoOption)
    }

    @Throws(Exception::class)
    private fun createUser(username: String, email: String, password: String): io.gitea.model.User {
        doCreateUser(username, email, password)
        val apiClient: ApiClient = GiteaContext.connect(giteaApiUrl!!, administratorToken!!)
        val userApi = UserApi(sudo(apiClient, username))
        return userApi.userGetCurrent()
    }

    // Gitea API methods
    private fun sudo(apiClient: ApiClient, username: String): ApiClient {
        val sudoParam = apiClient.getAuthentication("SudoParam") as ApiKeyAuth
        sudoParam.apiKey = username
        return apiClient
    }

    @Test
    @Throws(Exception::class)
    fun testLfs() {
        val storage = GiteaConfig.createLfsStorage(giteaUrl!!, testPublicRepository!!.fullName, administratorToken!!)
        val user = User.create(administrator, administrator, administrator, administrator, UserType.Gitea, LfsCredentials(administrator, administratorPassword))
        LfsLocalStorageTest.checkLfs(storage, user)
        LfsLocalStorageTest.checkLfs(storage, user)
        LfsLocalStorageTest.checkLocks(storage, user)
    }

    // Tests
    @Test
    @Throws(Exception::class)
    fun testApiConnectPassword() {
        val apiClient = ApiClient()
        apiClient.basePath = giteaApiUrl
        apiClient.setUsername(administrator)
        apiClient.setPassword(administratorPassword)
        val userApi = UserApi(apiClient)
        val user = userApi.userGetCurrent()
        Assert.assertNotNull(user)
        Assert.assertEquals(user.login, administrator)
    }

    @Test
    @Throws(Exception::class)
    fun testGiteaContextConnect() {
        val apiClient: ApiClient = GiteaContext.connect(giteaApiUrl!!, administratorToken!!)
        val userApi = UserApi(apiClient)
        val user = userApi.userGetCurrent()
        Assert.assertNotNull(user)
        Assert.assertEquals(user.login, administrator)
    }

    @Test
    @Throws(Exception::class)
    fun testCheckAdminLogin() {
        checkUser(administrator, administratorPassword)
    }

    @Throws(Exception::class)
    private fun checkUser(login: String, password: String) {
        createServer(administratorToken!!, null).use { server -> server.openSvnRepository(login, password).latestRevision }
    }

    // SvnTest Methods
    @Throws(Exception::class)
    private fun createServer(token: GiteaToken, mappingConfigCreator: Function<Path, RepositoryMappingConfig>?): SvnTestServer {
        val giteaConfig = GiteaConfig(giteaApiUrl!!, token)
        return SvnTestServer.createEmpty(GiteaUserDBConfig(), mappingConfigCreator, false, SvnTestServer.LfsMode.None, giteaConfig)
    }

    @Test
    @Throws(Exception::class)
    fun testCheckUserLogin() {
        checkUser(user, userPassword)
    }

    @Test
    fun testInvalidPassword() {
        Assert.expectThrows(SVNAuthenticationException::class.java) { checkUser(administrator, "wrongpassword") }
    }

    @Test
    fun testInvalidUser() {
        Assert.expectThrows(SVNAuthenticationException::class.java) { checkUser("wronguser", administratorPassword) }
    }

    @Test
    @Throws(Exception::class)
    fun testGiteaMapping() {
        createServer(administratorToken!!) { dir: Path? -> GiteaMappingConfig(dir!!, GitCreateMode.EMPTY) }.use { server ->
            // Test user can get own private repo
            openSvnRepository(server, testPrivateRepository!!, user, userPassword).latestRevision
            // Collaborator cannot get test's private repo
            Assert.assertThrows(SVNAuthenticationException::class.java) { openSvnRepository(server, testPrivateRepository!!, collaborator, collaboratorPassword).latestRevision }
            // Anyone can get public repo
            openSvnRepository(server, testPublicRepository!!, "anonymous", "nopassword").latestRevision
            // Collaborator can get public repo
            openSvnRepository(server, testPublicRepository!!, collaborator, collaboratorPassword).latestRevision
            // Add collaborator to private repo
            repoAddCollaborator(testPrivateRepository!!.owner.login, testPrivateRepository!!.name, collaborator)
            // Collaborator can get private repo
            openSvnRepository(server, testPrivateRepository!!, collaborator, collaboratorPassword).latestRevision
        }
    }

    @Throws(SVNException::class)
    private fun openSvnRepository(server: SvnTestServer, repository: Repository, username: String, password: String): SVNRepository {
        return SvnTestServer.openSvnRepository(server.getUrl(false).appendPath(repository.fullName + "/master", false), username, password)
    }

    @Throws(Exception::class)
    private fun repoAddCollaborator(owner: String, repo: String, collaborator: String) {
        val apiClient: ApiClient = GiteaContext.connect(giteaApiUrl!!, administratorToken!!)
        val repositoryApi = RepositoryApi(apiClient)
        val aco = AddCollaboratorOption()
        aco.permission = "write"
        repositoryApi.repoAddCollaborator(owner, repo, collaborator, aco)
    }

    @AfterClass
    fun after() {
        gitea?.stop()
        gitea = null
    }

    companion object {
        private val log = TestHelper.logger
        private const val administrator = "administrator"
        private const val administratorPassword = "administrator"
        private const val user = "testuser"
        private const val userPassword = "userPassword"
        private const val collaborator = "collaborator"
        private const val collaboratorPassword = "collaboratorPassword"
    }
}
