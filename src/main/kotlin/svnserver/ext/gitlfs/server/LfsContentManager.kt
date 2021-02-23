/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server

import org.apache.commons.io.IOUtils
import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.Constants
import ru.bozaro.gitlfs.common.data.Meta
import ru.bozaro.gitlfs.server.ContentManager
import ru.bozaro.gitlfs.server.ContentManager.Downloader
import ru.bozaro.gitlfs.server.ContentManager.Uploader
import ru.bozaro.gitlfs.server.ForbiddenError
import ru.bozaro.gitlfs.server.UnauthorizedError
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.ext.gitlfs.LfsAuthHelper
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.web.server.WebServer
import svnserver.repository.VcsAccess
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.servlet.http.HttpServletRequest

/**
 * ContentManager wrapper for shared LFS server implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LfsContentManager internal constructor(private val context: LocalContext, val storage: LfsStorage, private val tokenExpireSec: Long, private val tokenEnsureTime: Float) : ContentManager {
    @Throws(IOException::class, ForbiddenError::class, UnauthorizedError::class)
    override fun checkDownloadAccess(request: HttpServletRequest): Downloader {
        val user = checkDownload(request)
        val header: Map<String, String> = createHeader(request, user)
        return object : Downloader {
            @Throws(IOException::class)
            override fun openObject(hash: String): InputStream {
                val reader = storage.getReader(LfsStorage.OID_PREFIX + hash, -1) ?: throw FileNotFoundException(hash)
                return reader.openStream()
            }

            @Throws(IOException::class)
            override fun openObjectGzipped(hash: String): InputStream? {
                val reader = storage.getReader(LfsStorage.OID_PREFIX + hash, -1) ?: throw FileNotFoundException(hash)
                return reader.openGzipStream()
            }

            override fun createHeader(defaultHeader: Map<String, String>): Map<String, String> {
                return header
            }
        }
    }

    @Throws(IOException::class, UnauthorizedError::class, ForbiddenError::class)
    fun checkDownload(request: HttpServletRequest): User {
        val access = context.sure(VcsAccess::class.java)
        return checkAccess(request) { user: User, branch: String, path: String -> access.checkRead(user, branch, path) }
    }

    private fun createHeader(request: HttpServletRequest, user: User): Map<String, String> {
        val auth = request.getHeader(Constants.HEADER_AUTHORIZATION) ?: return emptyMap()
        return if (auth.startsWith(WebServer.AUTH_TOKEN)) {
            mapOf(Constants.HEADER_AUTHORIZATION to auth)
        } else {
            LfsAuthHelper.createTokenHeader(context.shared, user, LfsAuthHelper.getExpire(tokenExpireSec))
        }
    }

    @Throws(IOException::class, UnauthorizedError::class, ForbiddenError::class)
    private fun checkAccess(request: HttpServletRequest, checker: Checker): User {
        val user = getAuthInfo(request)
        try {
            // This is a *bit* of a hack.
            // If user accesses LFS, it means she is using git. If she uses git, she has whole repository contents.
            // If she has full repository contents, it doesn't make sense to apply path-based authorization.
            // Setups where where user has Git access but is not allowed to write via path-based authorization are declared bogus.
            checker.check(user, org.eclipse.jgit.lib.Constants.MASTER, "/")
        } catch (ignored: SVNException) {
            if (user.isAnonymous) {
                throw UnauthorizedError("Basic realm=\"" + context.shared.realm + "\"")
            } else {
                throw ForbiddenError()
            }
        }
        return user
    }

    private fun getAuthInfo(request: HttpServletRequest): User {
        val server = context.shared.sure(WebServer::class.java)
        val user = server.getAuthInfo(request.getHeader(Constants.HEADER_AUTHORIZATION), Math.round(tokenExpireSec * tokenEnsureTime))
        return user ?: User.anonymous
    }

    @Throws(IOException::class, ForbiddenError::class, UnauthorizedError::class)
    override fun checkUploadAccess(request: HttpServletRequest): Uploader {
        val user = checkUpload(request)
        val header = createHeader(request, user)
        return object : Uploader {
            @Throws(IOException::class)
            override fun saveObject(meta: Meta, content: InputStream) {
                storage.getWriter(Objects.requireNonNull(user)).use { writer ->
                    IOUtils.copy(content, writer)
                    writer.finish(LfsStorage.OID_PREFIX + meta.oid)
                }
            }

            override fun createHeader(defaultHeader: Map<String, String>): Map<String, String> {
                return header
            }
        }
    }

    @Throws(IOException::class, UnauthorizedError::class, ForbiddenError::class)
    fun checkUpload(request: HttpServletRequest): User {
        val access = context.sure(VcsAccess::class.java)
        return checkAccess(request) { user: User, branch: String, path: String -> access.checkWrite(user, branch, path) }
    }

    @Throws(IOException::class)
    override fun getMetadata(hash: String): Meta? {
        val reader = storage.getReader(LfsStorage.OID_PREFIX + hash, -1) ?: return null
        return Meta(reader.getOid(true), reader.size)
    }

    fun interface Checker {
        @Throws(SVNException::class, IOException::class)
        fun check(user: User, branch: String, path: String)
    }
}
