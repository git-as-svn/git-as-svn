/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network

import ru.bozaro.gitlfs.client.Client
import ru.bozaro.gitlfs.common.data.BatchItem
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import java.io.IOException
import java.io.InputStream

/**
 * Network storage reader.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class LfsHttpReader(private val lfsClient: Client, private val item: BatchItem) : LfsReader {
    @Throws(IOException::class)
    override fun openStream(): InputStream {
        return lfsClient.openObject(item, item)
    }

    override fun openGzipStream(): InputStream? {
        return null
    }

    override val size: Long
        get() = item.size

    override fun getOid(hashOnly: Boolean): String {
        return if (hashOnly) item.oid.substring(LfsStorage.OID_PREFIX.length) else item.oid
    }
}
