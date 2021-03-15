/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.tmatesoft.sqljet.core.internal.SqlJetPagerJournalMode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCompression
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc2.SvnOperationFactory
import svnserver.config.*
import svnserver.config.LocalUserDBConfig.UserEntry
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.ext.gitlfs.LocalLfsConfig
import svnserver.ext.gitlfs.storage.LfsStorageFactory
import svnserver.ext.gitlfs.storage.memory.LfsMemoryStorage
import svnserver.ext.web.config.WebServerConfig
import svnserver.repository.RepositoryMapping
import svnserver.repository.VcsAccess
import svnserver.repository.git.EmptyDirsSupport
import svnserver.repository.git.GitRepository
import svnserver.repository.git.push.GitPushEmbedded
import svnserver.server.SvnServer
import svnserver.tester.SvnTester
import java.nio.file.Path
import java.util.*
import java.util.function.Function

/**
 * Test subversion server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnTestServer private constructor(
    repository: Repository,
    branch: String?,
    prefix: String,
    safeBranch: Boolean,
    userDBConfig: UserDBConfig?,
    mappingConfigCreator: Function<Path, RepositoryMappingConfig>?,
    anonymousRead: Boolean,
    lfsMode: LfsMode,
    emptyDirs: EmptyDirsSupport,
    vararg shared: SharedConfig
) : SvnTester {
    val tempDirectory: Path
    val repository: Repository
    private var testBranch: String
    private val prefix: String
    private val server: SvnServer
    private val svnFactories = ArrayList<SvnOperationFactory>()
    private val safeBranch: Boolean
    private fun cleanupBranches(repository: Repository) {
        val branches = ArrayList<String>()
        for (ref in repository.refDatabase.getRefsByPrefix(Constants.R_HEADS + TEST_BRANCH_PREFIX)) {
            branches.add(ref.name.substring(Constants.R_HEADS.length))
        }
        if (branches.isNotEmpty()) {
            for (branch in branches) {
                log.info("Cleanup branch: {}", branch)
                try {
                    Git(repository)
                        .branchDelete()
                        .setBranchNames(branch)
                        .setForce(true)
                        .call()
                } catch (e: GitAPIException) {
                    log.error("Cleanup branch: $branch", e)
                }
            }
        }
    }

    val context: SharedContext
        get() = server.sharedContext

    @get:Throws(SVNException::class)
    override val url: SVNURL
        get() = getUrl(true)
    fun getUrl(withPrefix: Boolean): SVNURL {
        return SVNURL.create("svn", null, BIND_HOST, server.port, if (withPrefix) prefix else "", true)
    }
    override fun openSvnRepository(): SVNRepository {
        return openSvnRepository(USER_NAME, PASSWORD)
    }
    fun openSvnRepository(username: String, password: String): SVNRepository {
        return openSvnRepository(url, username, password)
    }
    override fun close() {
        shutdown(0)
        if (safeBranch) {
            Git(repository)
                .branchDelete()
                .setBranchNames(testBranch)
                .setForce(true)
                .call()
        }
        for (factory in svnFactories) {
            factory.dispose()
        }
        svnFactories.clear()
        repository.close()
        TestHelper.deleteDirectory(tempDirectory)
    }
    fun shutdown(millis: Int) {
        server.shutdown(millis.toLong())
    }

    fun createOperationFactory(): SvnOperationFactory {
        return createOperationFactory(USER_NAME, PASSWORD)
    }

    private fun createOperationFactory(username: String, password: String): SvnOperationFactory {
        val wcContext = SVNWCContext(DefaultSVNOptions(tempDirectory.toFile(), true), null)
        wcContext.setSqliteTemporaryDbInMemory(true)
        wcContext.setSqliteJournalMode(SqlJetPagerJournalMode.MEMORY)
        val factory = SvnOperationFactory(wcContext)
        factory.authenticationManager = BasicAuthenticationManager.newInstance(username, password.toCharArray())
        svnFactories.add(factory)
        return factory
    }
    fun startShutdown() {
        server.startShutdown()
    }

    enum class LfsMode {
        None, Memory, Local
    }

    private class TestRepositoryConfig(private val git: Repository, private val branch: String, private val prefix: String, private val anonymousRead: Boolean, private val emptyDirs: EmptyDirsSupport) : RepositoryMappingConfig {
        override fun create(context: SharedContext, canUseParallelIndexing: Boolean): RepositoryMapping<GitRepository> {
            val local = LocalContext(context, "test")
            local.add(VcsAccess::class.java, if (anonymousRead) VcsAccessEveryone.instance else VcsAccessNoAnonymous.instance)
            val repository = GitRepositoryConfig.createRepository(
                local,
                LfsStorageFactory.tryCreateStorage(local),
                git,
                GitPushEmbedded(local, null, false), setOf(branch),
                true,
                emptyDirs
            )
            return object : RepositoryMapping<GitRepository> {
                override val mapping: NavigableMap<String, GitRepository>
                    get() = TreeMap(Collections.singletonMap(prefix, repository))
            }
        }
    }

    companion object {
        const val USER_NAME_NO_MAIL = "nomail"
        const val PASSWORD = "passw0rd"
        const val USER_NAME = "tester"
        private val log = TestHelper.logger
        private const val REAL_NAME = "Test User"
        private const val EMAIL = "foo@bar.org"
        private const val TEST_BRANCH_PREFIX = "test_"
        fun openSvnRepository(url: SVNURL, username: String, password: String): SVNRepository {
            val repo = SVNRepositoryFactory.create(url)
            repo.authenticationManager = BasicAuthenticationManager.newInstance(username, password.toCharArray())
            return repo
        }
        fun createEmpty(): SvnTestServer {
            return createEmpty(null, false, LfsMode.Memory, EmptyDirsSupport.Disabled)
        }
        fun createEmpty(userDBConfig: UserDBConfig?, anonymousRead: Boolean, lfsMode: LfsMode, emptyDirs: EmptyDirsSupport, vararg shared: SharedConfig): SvnTestServer {
            return createEmpty(userDBConfig, null, anonymousRead, lfsMode, emptyDirs, *shared)
        }
        fun createEmpty(userDBConfig: UserDBConfig?, mappingConfigCreator: Function<Path, RepositoryMappingConfig>?, anonymousRead: Boolean, lfsMode: LfsMode, emptyDirs: EmptyDirsSupport, vararg shared: SharedConfig): SvnTestServer {
            return SvnTestServer(TestHelper.emptyRepository(), Constants.MASTER, "", false, userDBConfig, mappingConfigCreator, anonymousRead, lfsMode, emptyDirs, *shared)
        }
        fun createEmpty(userDBConfig: UserDBConfig?, mappingConfigCreator: Function<Path, RepositoryMappingConfig>?, anonymousRead: Boolean, lfsMode: LfsMode, vararg shared: SharedConfig): SvnTestServer {
            return createEmpty(userDBConfig, mappingConfigCreator, anonymousRead, lfsMode, EmptyDirsSupport.Disabled, *shared)
        }
        fun createEmpty(userDBConfig: UserDBConfig?, anonymousRead: Boolean, lfsMode: LfsMode, vararg shared: SharedConfig): SvnTestServer {
            return createEmpty(userDBConfig, null, anonymousRead, lfsMode, EmptyDirsSupport.Disabled, *shared)
        }
        fun createEmpty(emptyDirs: EmptyDirsSupport): SvnTestServer {
            return createEmpty(null, false, LfsMode.Memory, emptyDirs)
        }
        fun createEmpty(userDBConfig: UserDBConfig?, anonymousRead: Boolean, vararg shared: SharedConfig): SvnTestServer {
            return createEmpty(userDBConfig, null, anonymousRead, LfsMode.Memory, EmptyDirsSupport.Disabled, *shared)
        }
        fun createMasterRepository(): SvnTestServer {
            return SvnTestServer(FileRepository(TestHelper.findGitPath().toFile()), null, "", true, null, null, true, LfsMode.Memory, EmptyDirsSupport.Disabled)
        }

        private const val BIND_HOST = "127.0.0.2"
    }

    init {
        SVNFileUtil.setSleepForTimestamp(false)
        this.repository = repository
        this.safeBranch = safeBranch
        tempDirectory = TestHelper.createTempDir("git-as-svn")
        val srcBranch = branch ?: repository.branch
        if (safeBranch) {
            cleanupBranches(repository)
            testBranch = TEST_BRANCH_PREFIX + UUID.randomUUID().toString().replace("-".toRegex(), "").substring(0, 8)
            Git(repository)
                .branchCreate()
                .setName(testBranch)
                .setStartPoint(srcBranch)
                .call()
        } else {
            testBranch = srcBranch
        }
        this.prefix = "$prefix/$testBranch"
        val config = Config(BIND_HOST, 0)
        config.compressionLevel = SVNDeltaCompression.None
        config.cacheConfig = MemoryCacheConfig()
        when (lfsMode) {
            LfsMode.Local -> {
                config.shared.add(WebServerConfig(0))
                config.shared.add(LocalLfsConfig(tempDirectory.resolve("lfs").toString(), false))
            }
            LfsMode.Memory -> {
                config.shared.add(SharedConfig { context: SharedContext -> context.add(LfsStorageFactory::class.java, LfsStorageFactory { LfsMemoryStorage() }) })
            }
        }
        if (mappingConfigCreator != null) {
            config.repositoryMapping = mappingConfigCreator.apply(tempDirectory)
        } else {
            config.repositoryMapping = TestRepositoryConfig(repository, testBranch, prefix, anonymousRead, emptyDirs)
        }
        if (userDBConfig != null) {
            config.userDB = userDBConfig
        } else {
            config.userDB = LocalUserDBConfig(
                arrayOf(
                    UserEntry(USER_NAME, REAL_NAME, EMAIL, PASSWORD),
                    UserEntry(USER_NAME_NO_MAIL, REAL_NAME, null, PASSWORD)
                )
            )
        }
        Collections.addAll(config.shared, *shared)
        server = SvnServer(tempDirectory, config)
        server.start()
        log.info("Temporary server started (url: {}, path: {}, branch: {} as {})", url, repository.directory, srcBranch, testBranch)
        log.info("Temporary directory: {}", tempDirectory)
    }
}
