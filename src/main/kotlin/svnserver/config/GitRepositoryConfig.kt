/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config

import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.slf4j.Logger
import svnserver.Loggers
import svnserver.context.LocalContext
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsStorageFactory
import svnserver.repository.git.*
import svnserver.repository.git.filter.GitFilters
import svnserver.repository.git.push.GitPushEmbeddedConfig
import svnserver.repository.git.push.GitPusher
import svnserver.repository.locks.LocalLockManager
import svnserver.repository.locks.LockStorage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
class GitRepositoryConfig private constructor(private val createMode: GitCreateMode, branches: Array<String>) {
    private var branches = TreeSet<String>()
    var path: String = ".git"
    private var pusher: GitPusherConfig = GitPushEmbeddedConfig.instance
    private var renameDetection: Boolean = true
    private var emptyDirs: EmptyDirsSupport = EmptyDirsSupport.Disabled
    private var format = RepositoryFormat.Latest
    private var gitTreeEntryCacheStrategy: GitTreeEntryCacheStrategy = GitTreeEntryCacheStrategy.NoKeep

    @JvmOverloads
    constructor(createMode: GitCreateMode = GitCreateMode.ERROR) : this(createMode, arrayOf(Constants.MASTER))

    fun create(context: LocalContext, fullPath: Path = ConfigHelper.joinPath(context.shared.basePath, path), branches: Set<String> = this.branches): GitRepository {
        context.add(GitLocation::class.java, GitLocation(fullPath))
        val lfsStorage: LfsStorage? = LfsStorageFactory.tryCreateStorage(context)
        val git: Repository = createGit(context, fullPath)
        return createRepository(context, lfsStorage, git, pusher.create(context), branches, renameDetection, emptyDirs, format, gitTreeEntryCacheStrategy)
    }

    private fun createGit(context: LocalContext, fullPath: Path): Repository {
        if (!Files.exists(fullPath)) {
            log.info("[{}]: storage {} not found, create mode: {}", context.name, fullPath, createMode)
            return createMode.createRepository(fullPath, branches)
        }
        log.info("[{}]: using existing storage {}", context.name, fullPath)
        return FileRepository(fullPath.toFile())
    }

    companion object {
        private val log: Logger = Loggers.git

        @Throws(IOException::class)
        fun createRepository(
            context: LocalContext,
            lfsStorage: LfsStorage?,
            git: Repository,
            pusher: GitPusher,
            branches: Set<String>,
            renameDetection: Boolean,
            emptyDirs: EmptyDirsSupport,
            format: RepositoryFormat,
            gitTreeEntryCacheStrategy: GitTreeEntryCacheStrategy,
        ): GitRepository {
            val lockStorage: LockStorage = if (lfsStorage != null) {
                context.add(LfsStorage::class.java, lfsStorage)
                lfsStorage
            } else {
                LocalLockManager(LocalLockManager.getPersistentStorage(context))
            }
            val filters = GitFilters(context, lfsStorage)
            return GitRepository(context, git, pusher, branches, renameDetection, lockStorage, filters, emptyDirs, format, gitTreeEntryCacheStrategy)
        }
    }

    init {
        this.branches.addAll(branches)
    }
}
