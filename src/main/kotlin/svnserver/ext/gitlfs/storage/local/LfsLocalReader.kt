/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local

import org.apache.commons.io.IOUtils
import ru.bozaro.gitlfs.pointer.Constants
import ru.bozaro.gitlfs.pointer.Pointer
import svnserver.ext.gitlfs.LocalLfsConfig.LfsLayout
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LfsLocalReader private constructor(private val meta: Map<String, String>, private val file: Path, private val compressed: Boolean) : LfsReader {
    @Throws(IOException::class)
    override fun openStream(): InputStream {
        val result = Files.newInputStream(file)
        return if (compressed) GZIPInputStream(result) else result
    }

    @Throws(IOException::class)
    override fun openGzipStream(): InputStream? {
        return if (!compressed) null else Files.newInputStream(file)
    }

    override val size: Long
        get() = meta[Constants.SIZE]!!.toLong()

    override fun getOid(hashOnly: Boolean): String {
        val oid = meta[Constants.OID]
        return if (hashOnly) oid!!.substring(LfsStorage.OID_PREFIX.length) else oid!!
    }

    companion object {
        @Throws(IOException::class)
        fun create(layout: LfsLayout, dataRoot: Path, metaRoot: Path?, oid: String): LfsLocalReader? {
            var meta: Map<String, String>?
            val dataPath: Path? = LfsLocalStorage.getPath(layout, dataRoot, oid, "")
            if (metaRoot != null) {
                val metaPath: Path = LfsLocalStorage.getPath(layout, metaRoot, oid, ".meta") ?: return null
                try {
                    Files.newInputStream(metaPath).use { stream -> meta = Pointer.parsePointer(IOUtils.toByteArray(stream)) }
                } catch (ignored: NoSuchFileException) {
                    return null
                }
                if (meta == null) throw IOException("Corrupt meta file: $metaPath")
                if (meta!![Constants.OID] != oid) {
                    throw IOException("Corrupt meta file: " + metaPath + " - unexpected oid:" + meta!![Constants.OID])
                }
                val gzipPath: Path? = LfsLocalStorage.getPath(layout, dataRoot, oid, ".gz")
                if (gzipPath != null && Files.exists(gzipPath)) return LfsLocalReader(meta!!, gzipPath, true)
            } else {
                if (dataPath == null || !Files.isRegularFile(dataPath)) return null
                meta = hashMapOf(
                    Constants.OID to oid,
                    Constants.SIZE to Files.size(dataPath).toString()
                )
            }
            return if (dataPath != null && Files.isRegularFile(dataPath)) LfsLocalReader(meta!!, dataPath, false) else null
        }
    }
}
