/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.push

import com.google.common.base.Strings
import com.google.common.io.CharStreams
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import svnserver.Loggers
import svnserver.auth.User
import svnserver.config.ConfigHelper
import svnserver.context.LocalContext
import svnserver.repository.VcsAccess
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors

/**
 * Git push mode.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitPushEmbedded constructor(private val context: LocalContext, private val hooksPathOverride: String?, private val useHooksDir: Boolean) : GitPusher {
    @Throws(SVNException::class, IOException::class)
    override fun push(repository: Repository, commitId: ObjectId, branch: String, userInfo: User): Boolean {
        val refUpdate: RefUpdate = repository.updateRef(branch)
        refUpdate.oldObjectId
        refUpdate.setNewObjectId(commitId)
        runReceiveHook(repository, refUpdate, SVNErrorCode.REPOS_HOOK_FAILURE, "pre-receive", userInfo)
        runUpdateHook(repository, refUpdate, "update", userInfo)
        return when (val result = refUpdate.update()) {
            RefUpdate.Result.REJECTED, RefUpdate.Result.LOCK_FAILURE -> false
            RefUpdate.Result.NEW, RefUpdate.Result.FAST_FORWARD -> {
                runReceiveHook(repository, refUpdate, SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED, "post-receive", userInfo)
                true
            }
            else -> {
                log.error("Unexpected push error: {}", result)
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, result.name))
            }
        }
    }

    @Throws(SVNException::class)
    private fun runReceiveHook(repository: Repository, refUpdate: RefUpdate, svnErrorCode: SVNErrorCode, hook: String, userInfo: User) {
        runHook(repository, svnErrorCode, hook, userInfo) { processBuilder: ProcessBuilder ->
            val process: Process = processBuilder.start()
            OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).use { stdin ->
                stdin.write(getObjectId(refUpdate.oldObjectId))
                stdin.write(' '.code)
                stdin.write(getObjectId(refUpdate.newObjectId))
                stdin.write(' '.code)
                stdin.write(refUpdate.name)
                stdin.write('\n'.code)
            }
            process
        }
    }

    @Throws(SVNException::class)
    private fun runUpdateHook(repository: Repository, refUpdate: RefUpdate, hook: String, userInfo: User) {
        runHook(repository, SVNErrorCode.REPOS_HOOK_FAILURE, hook, userInfo) { processBuilder: ProcessBuilder ->
            processBuilder.command().addAll(
                arrayOf(
                    refUpdate.name,
                    getObjectId(refUpdate.oldObjectId),
                    getObjectId(refUpdate.newObjectId)
                )
            )
            processBuilder.start()
        }
    }

    @Throws(SVNException::class)
    private fun runHook(repository: Repository, hookErrorCode: SVNErrorCode, hook: String, userInfo: User, runner: HookRunner) {
        val repositoryDir: Path = (if (repository.directory == null) null else repository.directory.toPath()) ?: return // We don't have a dir where to run hooks :(
        val hooksPath: String = getHooksPath(repository)
        val hooksDir: Path = ConfigHelper.joinPath(repositoryDir, hooksPath)
        val startTime: Long = System.currentTimeMillis()
        try {
            val mainHook: Path = ConfigHelper.joinPath(hooksDir, hook)
            if (Files.exists(mainHook)) runHook(hookErrorCode, userInfo, runner, repositoryDir, mainHook)
            if (useHooksDir) {
                val scriptDir: Path = ConfigHelper.joinPath(hooksDir, "$hook.d")
                if (Files.exists(scriptDir)) {
                    var scripts: TreeSet<Path>

                    // See https://docs.gitlab.com/ee/administration/server_hooks.html#chained-hooks-support
                    try {
                        Files.list(scriptDir).filter { path: Path -> Files.isExecutable(path) }.filter { path: Path -> !path.fileName.endsWith("~") }.use { scriptStream -> scripts = scriptStream.collect(Collectors.toCollection { TreeSet() }) }
                    } catch (e: IOException) {
                        throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e))
                    }
                    for (script: Path in scripts) runHook(hookErrorCode, userInfo, runner, repositoryDir, script)
                }
            }
        } finally {
            val endTime: Long = System.currentTimeMillis()
            log.info("{} hook for repository {} took {}ms", hook, repository.toString(), (endTime - startTime))
        }
    }

    @Throws(SVNException::class)
    private fun runHook(hookErrorCode: SVNErrorCode, userInfo: User, runner: HookRunner, repositoryDir: Path, script: Path) {
        val processBuilder: ProcessBuilder = ProcessBuilder(script.toString())
            .directory(repositoryDir.toFile())
            .redirectErrorStream(true)
        processBuilder.environment()["LANG"] = "en_US.utf8"
        try {
            context.sure(VcsAccess::class.java).updateEnvironment(processBuilder.environment(), userInfo)
        } catch (e: IOException) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e))
        }
        var process: Process? = null
        try {
            process = runner.exec(processBuilder)

            // Prevent hanging if hook tries to read from stdin
            process.outputStream.close()
            var hookMessage: String
            InputStreamReader(process.inputStream, StandardCharsets.UTF_8).use { stdout -> hookMessage = CharStreams.toString(stdout) }
            val exitCode: Int = process.waitFor()
            if (exitCode != 0) {
                throw SVNException(SVNErrorMessage.create(hookErrorCode, String.format("Hook %s failed with output:\n%s", script, hookMessage)))
            }
        } catch (e: InterruptedException) {
            log.error("Hook failed: $script", e)
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e))
        } catch (e: IOException) {
            log.error("Hook failed: $script", e)
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e))
        } finally {
            process?.destroyForcibly()
        }
    }

    private fun getHooksPath(repository: Repository): String {
        if (!Strings.isNullOrEmpty(hooksPathOverride)) return (hooksPathOverride)!!
        val hooksPathFromConfig: String? = repository.config.getString(ConfigConstants.CONFIG_CORE_SECTION, null, "hooksPath")
        if (!Strings.isNullOrEmpty(hooksPathFromConfig)) return hooksPathFromConfig!!
        return "hooks"
    }

    fun interface HookRunner {
        @Throws(IOException::class)
        fun exec(processBuilder: ProcessBuilder): Process
    }

    companion object {
        private val log: Logger = Loggers.git
        private fun getObjectId(objectId: ObjectId?): String {
            return if (objectId == null) ObjectId.zeroId().name else objectId.name
        }
    }
}
