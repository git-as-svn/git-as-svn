/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter

import org.eclipse.jgit.lib.ObjectId
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.repository.git.GitObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Gzip filter. Useful for testing.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitFilterGzip(context: LocalContext) : GitFilter {
    private val cacheMd5: MutableMap<String, String>
    private val cacheSize: MutableMap<String, Long>
    override val name: String
        get() {
            return "gzip"
        }

    @Throws(IOException::class)
    override fun getMd5(objectId: GitObject<out ObjectId>): String {
        return GitFilterHelper.getMd5(this, cacheMd5, cacheSize, objectId)
    }

    @Throws(IOException::class)
    override fun getSize(objectId: GitObject<out ObjectId>): Long {
        return GitFilterHelper.getSize(this, cacheMd5, cacheSize, objectId)
    }

    @Throws(IOException::class)
    override fun inputStream(objectId: GitObject<out ObjectId>): InputStream {
        return GZIPInputStream(objectId.openObject().openStream())
    }

    @Throws(IOException::class)
    override fun outputStream(stream: OutputStream, user: User): OutputStream {
        return GZIPOutputStream(stream)
    }

    init {
        cacheMd5 = GitFilterHelper.getCacheMd5(this, context.shared.cacheDB)
        cacheSize = GitFilterHelper.getCacheSize(this, context.shared.cacheDB)
    }
}
