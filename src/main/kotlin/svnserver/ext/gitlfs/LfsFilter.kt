/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs

import org.apache.commons.io.IOUtils
import org.eclipse.jgit.lib.ObjectId
import ru.bozaro.gitlfs.common.data.Meta
import ru.bozaro.gitlfs.pointer.Constants
import ru.bozaro.gitlfs.pointer.Pointer
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.ext.gitlfs.server.LfsServer
import svnserver.ext.gitlfs.server.LfsServerEntry
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsWriter
import svnserver.repository.SvnForbiddenException
import svnserver.repository.git.GitObject
import svnserver.repository.git.filter.GitFilter
import svnserver.repository.git.filter.GitFilterHelper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Filter for Git LFS.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LfsFilter(context: LocalContext, private val storage: LfsStorage?) : GitFilter {
    private val cacheMd5: MutableMap<String, String>
    override val name: String
        get() = "lfs"

    @Throws(IOException::class)
    override fun getMd5(objectId: GitObject<out ObjectId>): String {
        val loader = objectId.openObject()
        loader.openStream().use { stream ->
            val meta = parseMeta(stream)
            if (meta != null) {
                val md5 = getReader(meta).md5
                if (md5 != null) return md5
            }
        }
        return GitFilterHelper.getMd5(this, cacheMd5, null, objectId)
    }

    @Throws(IOException::class)
    override fun getSize(objectId: GitObject<out ObjectId>): Long {
        val loader = objectId.openObject()
        loader.openStream().use { stream ->
            val meta = parseMeta(stream)
            if (meta != null) return meta.size
        }
        return loader.size
    }

    @Throws(IOException::class)
    override fun inputStream(objectId: GitObject<out ObjectId>): InputStream {
        val loader = objectId.openObject()
        loader.openStream().use { stream ->
            val header = ByteArray(Constants.POINTER_MAX_SIZE)
            val length = IOUtils.read(stream, header, 0, header.size)
            if (length < header.size) {
                val meta = parseMeta(header, length)
                if (meta != null) return getReader(meta).openStream()
            }

            // We need to re-open stream
            return loader.openStream()
        }
    }

    @Throws(IOException::class)
    override fun outputStream(stream: OutputStream, user: User): OutputStream {
        return TemporaryOutputStream(getStorage().getWriter(user), stream)
    }

    @Throws(IOException::class)
    private fun getReader(meta: Meta): LfsReader {
        return getStorage().getReader(meta.oid, meta.size) ?: throw SvnForbiddenException()
    }

    private fun getStorage(): LfsStorage {
        checkNotNull(storage) { "LFS is not configured" }
        return storage
    }

    private class TemporaryOutputStream(private val dataStream: LfsWriter, private val pointerStream: OutputStream) : OutputStream() {
        private var size: Long = 0

        @Throws(IOException::class)
        override fun write(b: Int) {
            dataStream.write(b)
            size++
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            dataStream.write(b, off, len)
            size += len.toLong()
        }

        @Throws(IOException::class)
        override fun flush() {
            dataStream.flush()
        }

        @Throws(IOException::class)
        override fun close() {
            pointerStream.use { pointerOut ->
                var pointer: Map<String, String>?
                dataStream.use { dataOut -> pointer = if (size <= 0) null else Pointer.createPointer(dataOut.finish(null), size) }
                if (pointer != null) pointerOut.write(Pointer.serializePointer(pointer!!))
            }
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun parseMeta(stream: InputStream): Meta? {
            val header = ByteArray(Constants.POINTER_MAX_SIZE)
            val length = IOUtils.read(stream, header, 0, header.size)
            return if (length >= header.size) null else parseMeta(header, length)
        }

        private fun parseMeta(header: ByteArray, length: Int): Meta? {
            val pointer = Pointer.parsePointer(header, 0, length) ?: return null
            val oid = pointer[Constants.OID]
            val size = pointer[Constants.SIZE]!!.toLong()
            return Meta(oid!!, size)
        }
    }

    init {
        cacheMd5 = GitFilterHelper.getCacheMd5(this, context.shared.cacheDB)
        val lfsServer = context.shared[LfsServer::class.java]
        if (storage != null && lfsServer != null) {
            context.add(LfsServerEntry::class.java, LfsServerEntry(lfsServer, context, storage))
        }
    }
}
