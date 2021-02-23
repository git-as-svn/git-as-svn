/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.push

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import svnserver.Loggers
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.repository.VcsAccess
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Git push by native git client.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitPushNative constructor(private val context: LocalContext) : GitPusher {
    @Throws(SVNException::class, IOException::class)
    override fun push(repository: Repository, commitId: ObjectId, branch: String, userInfo: User): Boolean {
        try {
            repository.directory
            val processBuilder: ProcessBuilder = ProcessBuilder("git", "push", "--porcelain", "--quiet", ".", commitId.name() + ":" + branch)
                .directory(repository.directory)
                .redirectErrorStream(true)
            processBuilder.environment()["LANG"] = "en_US.utf8"
            context.sure(VcsAccess::class.java).updateEnvironment(processBuilder.environment(), userInfo)
            val process: Process = processBuilder.start()
            val resultBuilder: StringBuilder = StringBuilder()
            val hookBuilder: StringBuilder = StringBuilder()
            try {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { stdout ->
                    for (line in stdout.lines()) {
                        if (line.startsWith(HOOK_MESSAGE_PREFIX)) {
                            if (hookBuilder.isNotEmpty()) hookBuilder.append('\n')
                            hookBuilder.append(line.substring(HOOK_MESSAGE_PREFIX.length + 1))
                        }
                        if (line.startsWith(SYSTEM_MESSAGE_PREFIX)) {
                            // System message like:
                            // !	2d1ed4dcc45bef07f6dfffabe7d3ff53aa147705:refs/heads/local	[remote rejected] (pre-receive hook declined)
                            // !	75cad4dcb5f6982a1f2df073157f3aa2083ae272:refs/heads/local	[rejected] (non-fast-forward)
                            if (resultBuilder.isNotEmpty()) resultBuilder.append('\n')
                            resultBuilder.append(line.substring(SYSTEM_MESSAGE_PREFIX.length + 1))
                        }
                    }
                }
                val exitCode: Int = process.waitFor()
                if (exitCode == 0) {
                    return true
                }
            } finally {
                process.destroyForcibly()
            }
            val resultMessage: String = resultBuilder.toString()
            if (resultMessage.contains("non-fast-forward")) {
                return false
            }
            if (resultMessage.contains("hook")) {
                val hookMessage: String = hookBuilder.toString()
                log.warn("Push rejected by hook:\n{}", hookMessage)
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.REPOS_HOOK_FAILURE, "Commit blocked by hook with output:\n$hookMessage"))
            }
            log.error("Unknown git push result:\n{}", resultMessage)
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, resultMessage))
        } catch (e: InterruptedException) {
            e.printStackTrace()
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e))
        }
    }

    companion object {
        private val log: Logger = Loggers.git
        private const val HOOK_MESSAGE_PREFIX: String = "remote:"
        private const val SYSTEM_MESSAGE_PREFIX: String = "!"
    }
}
