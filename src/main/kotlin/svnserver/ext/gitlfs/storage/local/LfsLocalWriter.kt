/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local

import org.apache.commons.codec.binary.Hex
import org.apache.commons.collections4.trie.PatriciaTrie
import ru.bozaro.gitlfs.common.JsonHelper
import ru.bozaro.gitlfs.pointer.Constants
import ru.bozaro.gitlfs.pointer.Pointer
import svnserver.HashHelper
import svnserver.auth.User
import svnserver.ext.gitlfs.LocalLfsConfig.LfsLayout
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsWriter
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.zip.GZIPOutputStream

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LfsLocalWriter internal constructor(private val layout: LfsLayout, private val dataRoot: Path, private val metaRoot: Path?, private val compress: Boolean, private val user: User?) : LfsWriter() {

    private val dataTemp: Path
    private val metaTemp: Path?
    private val digestMd5: MessageDigest
    private val digestSha: MessageDigest
    private var dataStream: OutputStream? = null
    private var size: Long

    @Throws(IOException::class)
    override fun write(b: Int) {
        checkNotNull(dataStream)
        dataStream!!.write(b)
        digestMd5.update(b.toByte())
        digestSha.update(b.toByte())
        size += 1
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        checkNotNull(dataStream)
        dataStream!!.write(b, off, len)
        digestMd5.update(b, off, len)
        digestSha.update(b, off, len)
        size += len.toLong()
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            if (dataStream != null) {
                dataStream!!.close()
                dataStream = null
            }
        } finally {
            Files.deleteIfExists(dataTemp)
        }
    }

    @Throws(IOException::class)
    override fun finish(expectedOid: String?): String {
        checkNotNull(dataStream)
        return try {
            dataStream!!.close()
            dataStream = null
            val sha = digestSha.digest()
            val md5 = digestMd5.digest()
            val oid: String = LfsStorage.OID_PREFIX + Hex.encodeHexString(sha)
            if (expectedOid != null && expectedOid != oid) {
                throw IOException("Invalid stream checksum: expected $expectedOid, but actual $oid")
            }

            // Write file data
            val dataPath: Path = LfsLocalStorage.getPath(layout, dataRoot, oid, if (compress) ".gz" else "") ?: throw IllegalStateException()
            try {
                Files.createDirectories(dataPath.parent)
                Files.move(dataTemp, dataPath)
            } catch (e: IOException) {
                if (!Files.isRegularFile(dataPath)) throw e
            }

            // Write metadata
            if (metaRoot != null) {
                val metaPath: Path = LfsLocalStorage.getPath(layout, metaRoot, oid, ".meta") ?: throw IllegalStateException()
                Files.createDirectories(metaPath.parent)
                if (!Files.exists(metaPath) && metaTemp != null) {
                    try {
                        Files.newOutputStream(metaTemp).use { stream ->
                            val map = PatriciaTrie<String>()
                            map[Constants.SIZE] = size.toString()
                            map[Constants.OID] = oid
                            map[LfsLocalStorage.HASH_MD5] = Hex.encodeHexString(md5)
                            map[LfsLocalStorage.CREATE_TIME] = JsonHelper.dateFormat.format(Date())
                            if (user != null && !user.isAnonymous) {
                                if (user.email != null) {
                                    map[LfsLocalStorage.META_EMAIL] = user.email
                                }
                                map[LfsLocalStorage.META_USER_NAME] = user.username
                                map[LfsLocalStorage.META_REAL_NAME] = user.realName
                            }
                            stream.write(Pointer.serializePointer(map))
                            stream.close()
                            try {
                                Files.move(metaTemp, metaPath)
                            } catch (e: IOException) {
                                if (!Files.isRegularFile(metaPath)) {
                                    throw e
                                }
                                Unit
                            }
                        }
                    } finally {
                        Files.deleteIfExists(metaTemp)
                    }
                }
            }
            oid
        } finally {
            Files.deleteIfExists(dataTemp)
        }
    }

    init {
        val prefix = UUID.randomUUID().toString()
        dataTemp = Files.createDirectories(dataRoot.resolve("tmp")).resolve("$prefix.tmp")
        metaTemp = if (metaRoot == null) null else Files.createDirectories(metaRoot.resolve("tmp")).resolve("$prefix.tmp")
        digestMd5 = HashHelper.md5()
        digestSha = HashHelper.sha256()
        size = 0
        dataStream = if (compress) {
            GZIPOutputStream(Files.newOutputStream(dataTemp))
        } else {
            Files.newOutputStream(dataTemp)
        }
    }
}
