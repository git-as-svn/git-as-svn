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
import org.eclipse.jgit.lib.ObjectReader
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.repository.git.GitObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StreamCorruptedException
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Get object for symbolic link.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitFilterLink constructor(context: LocalContext) : GitFilter {
    private val cacheMd5: MutableMap<String, String>
    override val name: String
        get() {
            return "link"
        }

    @Throws(IOException::class)
    override fun getMd5(objectId: GitObject<out ObjectId>): String {
        return GitFilterHelper.getMd5(this, cacheMd5, null, objectId)
    }

    @Throws(IOException::class)
    override fun getSize(objectId: GitObject<out ObjectId>): Long {
        val reader: ObjectReader = objectId.repo.newObjectReader()
        return reader.getObjectSize(objectId.`object`, Constants.OBJ_BLOB) + LINK_PREFIX.size
    }

    @Throws(IOException::class)
    override fun inputStream(objectId: GitObject<out ObjectId>): InputStream {
        return InputWrapper(objectId.openObject().openStream())
    }

    override fun outputStream(stream: OutputStream, user: User): OutputStream {
        return OutputWrapper(stream)
    }

    private class InputWrapper(private val inner: InputStream) : InputStream() {
        private var offset: Int = 0

        @Throws(IOException::class)
        override fun read(): Int {
            if (offset >= LINK_PREFIX.size) {
                return inner.read()
            }
            return LINK_PREFIX[offset++].toInt() and 0xFF
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (offset >= LINK_PREFIX.size) {
                return super.read(buffer, off, len)
            }
            val size: Int = min(len, LINK_PREFIX.size - offset)
            System.arraycopy(LINK_PREFIX, 0, buffer, off, size)
            offset += size
            if (size < len) {
                val bytes: Int = inner.read(buffer, off + size, len - size)
                if (bytes > 0) {
                    return size + bytes
                }
            }
            return size
        }

        @Throws(IOException::class)
        override fun close() {
            inner.close()
        }
    }

    private class OutputWrapper(private val inner: OutputStream) : OutputStream() {
        private var offset: Int = 0

        @Throws(IOException::class)
        override fun write(b: Int) {
            if (offset >= LINK_PREFIX.size) {
                inner.write(b)
                return
            }
            if ((LINK_PREFIX[offset++].toInt() and 0xFF) != b) {
                throw StreamCorruptedException("Link entry has invalid content prefix.")
            }
        }

        @Throws(IOException::class)
        override fun write(buffer: ByteArray, off: Int, len: Int) {
            if (offset >= LINK_PREFIX.size) {
                super.write(buffer, off, len)
                return
            }
            val size: Int = min(len, LINK_PREFIX.size - offset)
            for (i in 0 until size) {
                if ((LINK_PREFIX[offset + i].toInt() and 0xFF) != buffer[off + i].toInt()) {
                    throw StreamCorruptedException("Link entry has invalid content prefix.")
                }
            }
            offset += size
            if (size < len) {
                inner.write(buffer, off + size, len - size)
            }
        }

        @Throws(IOException::class)
        override fun flush() {
            inner.flush()
        }

        @Throws(IOException::class)
        override fun close() {
            inner.close()
        }
    }

    companion object {
        private val LINK_PREFIX: ByteArray = "link ".toByteArray(StandardCharsets.ISO_8859_1)
    }

    init {
        cacheMd5 = GitFilterHelper.getCacheMd5(this, context.shared.cacheDB)
    }
}
