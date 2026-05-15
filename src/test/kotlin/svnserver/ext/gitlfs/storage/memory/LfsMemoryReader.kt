/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory

import org.apache.commons.codec.binary.Hex
import svnserver.HashHelper.md5
import svnserver.HashHelper.sha256
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class LfsMemoryReader(private val content: ByteArray) : LfsReader {
    override fun openStream(): InputStream {
        return ByteArrayInputStream(content)
    }

    override fun openGzipStream(): InputStream? {
        return null
    }

    override val size: Long
        get() = content.size.toLong()

    override fun getOid(hashOnly: Boolean): String {
        return if (hashOnly) {
            sha
        } else {
            LfsStorage.OID_PREFIX + sha
        }
    }

    private val sha: String
        get() = Hex.encodeHexString(sha256().digest(content))
}
