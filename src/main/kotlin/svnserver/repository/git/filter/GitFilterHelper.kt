/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter

import org.eclipse.jgit.lib.ObjectId
import org.mapdb.DB
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import svnserver.HashHelper
import svnserver.StringHelper
import svnserver.repository.git.GitObject
import java.io.IOException
import java.security.MessageDigest

/**
 * Helper for common filter functionality.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object GitFilterHelper {
    private const val BUFFER_SIZE: Int = 32 * 1024

    @Throws(IOException::class)
    fun getSize(filter: GitFilter, cacheMd5: MutableMap<String, String>, cacheSize: MutableMap<String, Long>, objectId: GitObject<out ObjectId>): Long {
        val size: Long? = cacheSize[objectId.`object`.name()]
        if (size != null) {
            return size
        }
        return createMetadata(objectId, filter, cacheMd5, cacheSize).size
    }

    @Throws(IOException::class)
    private fun createMetadata(objectId: GitObject<out ObjectId>, filter: GitFilter, cacheMd5: MutableMap<String, String>?, cacheSize: MutableMap<String, Long>?): Metadata {
        val buffer = ByteArray(BUFFER_SIZE)
        filter.inputStream(objectId).use { stream ->
            val digest: MessageDigest? = if (cacheMd5 != null) HashHelper.md5() else null
            var totalSize: Long = 0
            while (true) {
                val bytes: Int = stream.read(buffer)
                if (bytes <= 0) break
                digest?.update(buffer, 0, bytes)
                totalSize += bytes.toLong()
            }
            val md5: String?
            if ((cacheMd5 != null) && (digest != null)) {
                md5 = StringHelper.toHex(digest.digest())
                cacheMd5.putIfAbsent(objectId.`object`.name(), md5)
            } else {
                md5 = null
            }
            cacheSize?.putIfAbsent(objectId.`object`.name(), totalSize)
            return Metadata(totalSize, md5)
        }
    }

    @Throws(IOException::class)
    fun getMd5(filter: GitFilter, cacheMd5: MutableMap<String, String>, cacheSize: MutableMap<String, Long>?, objectId: GitObject<out ObjectId>): String {
        val md5: String? = cacheMd5[objectId.`object`.name()]
        if (md5 != null) {
            return md5
        }
        return (createMetadata(objectId, filter, cacheMd5, cacheSize).md5)!!
    }

    fun getCacheMd5(filter: GitFilter, cacheDb: DB): HTreeMap<String, String> {
        return cacheDb.hashMap("cache.filter." + filter.name + ".md5", Serializer.STRING, Serializer.STRING).createOrOpen()
    }

    fun getCacheSize(filter: GitFilter, cacheDb: DB): HTreeMap<String, Long> {
        return cacheDb.hashMap("cache.filter." + filter.name + ".size", Serializer.STRING, Serializer.LONG).createOrOpen()
    }

    private class Metadata(val size: Long, val md5: String?)
}
