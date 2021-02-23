/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network

import org.apache.commons.codec.binary.Hex
import ru.bozaro.gitlfs.client.Client
import ru.bozaro.gitlfs.common.data.BatchReq
import ru.bozaro.gitlfs.common.data.Meta
import ru.bozaro.gitlfs.common.data.Operation
import svnserver.HashHelper
import svnserver.TemporaryOutputStream
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsWriter
import java.io.IOException
import java.security.MessageDigest

/**
 * Network storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LfsHttpWriter internal constructor(private val lfsClient: Client) : LfsWriter() {
    private val content: TemporaryOutputStream = TemporaryOutputStream()
    private val digestSha: MessageDigest = HashHelper.sha256()

    @Throws(IOException::class)
    override fun write(b: Int) {
        content.write(b)
        digestSha.update(b.toByte())
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray, off: Int, len: Int) {
        content.write(buffer, off, len)
        digestSha.update(buffer, off, len)
    }

    @Throws(IOException::class)
    override fun finish(expectedOid: String?): String {
        val sha = Hex.encodeHexString(digestSha.digest())
        val oid: String = LfsStorage.OID_PREFIX + sha
        if (expectedOid != null && expectedOid != oid) {
            throw IOException("Invalid stream checksum: expected " + expectedOid + ", but actual " + LfsStorage.OID_PREFIX + sha)
        }
        val batchRes = lfsClient.postBatch(BatchReq(Operation.Upload, listOf(Meta(sha, content.size()))))
        if (batchRes.objects.isEmpty()) throw IOException(String.format("Empty batch response while uploading %s", sha))
        for (batchItem in batchRes.objects) {
            if (batchItem.error != null) throw IOException(String.format("LFS error[%s]: %s", batchItem.error!!.code, batchItem.error!!.message))
            lfsClient.putObject({ content.toInputStream() }, batchItem, batchItem)
        }
        return oid
    }
}
