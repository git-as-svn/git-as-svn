/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCompression
import svnserver.Loggers
import svnserver.StringHelper
import svnserver.auth.User
import svnserver.parser.SvnServerParser
import svnserver.parser.SvnServerWriter
import svnserver.repository.RepositoryInfo
import svnserver.repository.VcsAccess
import svnserver.repository.git.GitBranch
import svnserver.repository.git.GitFile
import svnserver.server.command.BaseCmd
import svnserver.server.msg.ClientInfo
import svnserver.server.step.Step
import java.io.IOException
import java.util.*

/**
 * SVN client session context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class SessionContext constructor(
    val parser: SvnServerParser,
    val writer: SvnServerWriter,
    val server: SvnServer,
    repositoryInfo: RepositoryInfo,
    clientInfo: ClientInfo
) {
    private val stepStack: Deque<Step> = ArrayDeque()
    private val repositoryInfo: RepositoryInfo
    private val capabilities: Set<String>
    private val acl: VcsAccess
    var user: User
        private set
    private var parent: String? = null
    val branch: GitBranch
        get() {
            return repositoryInfo.branch
        }

    @Throws(SVNException::class)
    fun setParent(url: SVNURL) {
        parent = getRepositoryPath(url)
    }

    @Throws(SVNException::class)
    private fun getRepositoryPath(url: SVNURL): String {
        val root: String = repositoryInfo.baseUrl.path
        val path: String = url.path
        if (!path.startsWith(root)) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Invalid relative path: $path (base: $root)"))
        }
        if (root.length == path.length) {
            return ""
        }
        val hasSlash: Boolean = root.endsWith("/")
        if (!hasSlash && (path[root.length] != '/')) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Invalid relative path: $path (base: $root)"))
        }
        return StringHelper.normalize(path.substring(root.length))
    }

    val compression: SVNDeltaCompression
        get() {
            if (server.compressionLevel >= SVNDeltaCompression.LZ4 && capabilities.contains(SvnServer.svndiff2Capability))
                return SVNDeltaCompression.LZ4

            if (server.compressionLevel >= SVNDeltaCompression.Zlib && capabilities.contains(SvnServer.svndiff1Capability))
                return SVNDeltaCompression.Zlib

            return SVNDeltaCompression.None
        }

    @Throws(IOException::class, SVNException::class)
    fun authenticate(allowAnonymous: Boolean) {
        if (!user.isAnonymous) throw IllegalStateException()
        user = server.authenticate(this, allowAnonymous and canRead(getRepositoryPath("")))
    }

    @Throws(IOException::class)
    fun canRead(path: String): Boolean {
        return acl.canRead(user, branch.shortBranchName, path)
    }

    fun getRepositoryPath(localPath: String): String {
        return StringHelper.joinPath(parent!!, localPath)
    }

    fun push(step: Step) {
        stepStack.push(step)
    }

    fun poll(): Step? {
        return stepStack.poll()
    }

    /**
     * Get repository file.
     *
     * @param rev  Target revision.
     * @param path Target path or url.
     * @return Return file object.
     */
    @Throws(SVNException::class, IOException::class)
    fun getFile(rev: Int, path: String): GitFile? {
        return branch.getRevisionInfo(rev).getFile(getRepositoryPath(path))
    }

    @Throws(SVNException::class, IOException::class)
    fun getFile(rev: Int, url: SVNURL): GitFile? {
        val path: String = getRepositoryPath(url)
        checkRead(path)
        return branch.getRevisionInfo(rev).getFile(path)
    }

    @Throws(SVNException::class, IOException::class)
    fun checkRead(path: String) {
        acl.checkRead(user, branch.shortBranchName, path)
    }

    @Throws(SVNException::class, IOException::class)
    fun checkWrite(path: String) {
        acl.checkWrite(user, branch.shortBranchName, path)
    }

    @Throws(IOException::class)
    fun skipUnsupportedCommand(cmd: String) {
        log.error("Unsupported command: {}", cmd)
        BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD, "Unsupported command: $cmd"))
        parser.skipItems()
    }

    companion object {
        private val log: Logger = Loggers.svn
    }

    init {
        user = User.anonymous
        this.repositoryInfo = repositoryInfo
        acl = branch.repository.context.sure(VcsAccess::class.java)
        setParent(clientInfo.url)
        capabilities = clientInfo.capabilities.toHashSet()
    }
}
