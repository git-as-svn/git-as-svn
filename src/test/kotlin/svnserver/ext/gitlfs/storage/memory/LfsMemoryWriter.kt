/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory

import org.apache.commons.codec.binary.Hex
import svnserver.HashHelper.sha256
import svnserver.ext.gitlfs.storage.LfsWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class LfsMemoryWriter(private val storage: ConcurrentHashMap<String, ByteArray>) : LfsWriter() {
    private var stream: ByteArrayOutputStream?
    override fun write(b: Int) {
        checkNotNull(stream)
        stream!!.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        checkNotNull(stream)
        stream!!.write(b, off, len)
    }

    override fun close() {
        stream = null
    }
    override fun finish(expectedOid: String?): String {
        checkNotNull(stream)
        val content = stream!!.toByteArray()
        val result = Hex.encodeHexString(sha256().digest(content))
        val oid = OID_PREFIX + result
        if (expectedOid != null && expectedOid != oid) {
            throw IOException("Invalid stream checksum: expected $expectedOid, but actual $oid")
        }
        storage.putIfAbsent(oid, content)
        stream = null
        return oid
    }

    companion object {
        private const val OID_PREFIX = "sha256:"
    }

    init {
        stream = ByteArrayOutputStream()
    }
}
