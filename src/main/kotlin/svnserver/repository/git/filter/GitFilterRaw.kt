/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.ObjectReader
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.repository.git.GitObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Get object as is.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitFilterRaw constructor(context: LocalContext) : GitFilter {
    private val cacheMd5: MutableMap<String, String>
    override val name: String
        get() {
            return "raw"
        }

    @Throws(IOException::class)
    override fun getMd5(objectId: GitObject<out ObjectId>): String {
        return GitFilterHelper.getMd5(this, cacheMd5, null, objectId)
    }

    @Throws(IOException::class)
    override fun getSize(objectId: GitObject<out ObjectId>): Long {
        val reader: ObjectReader = objectId.repo.newObjectReader()
        return reader.getObjectSize(objectId.`object`, Constants.OBJ_BLOB)
    }

    @Throws(IOException::class)
    override fun inputStream(objectId: GitObject<out ObjectId>): InputStream {
        val loader: ObjectLoader = objectId.openObject()
        return loader.openStream()
    }

    override fun outputStream(stream: OutputStream, user: User): OutputStream {
        return stream
    }

    init {
        cacheMd5 = GitFilterHelper.getCacheMd5(this, context.shared.cacheDB)
    }
}
