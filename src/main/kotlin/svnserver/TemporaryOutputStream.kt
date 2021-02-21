/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import org.jetbrains.annotations.TestOnly
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min

/**
 * Stream for write-then-read functionality.
 *
 * @author Artem V. Navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class TemporaryOutputStream @JvmOverloads constructor(private val maxMemorySize: Int = MAX_MEMORY_SIZE) : OutputStream() {
    private val memoryStream: ByteArrayOutputStream = ByteArrayOutputStream()
    private var file: Path? = null
    private var fileOutputStream: OutputStream? = null
    private var totalSize: Long = 0
    private var closed: Boolean = false

    @Throws(IOException::class)
    override fun write(b: Int) {
        if (closed) throw IOException()
        if (memoryStream.size() < maxMemorySize) {
            memoryStream.write(b)
            totalSize++
            return
        }
        ensureFile().write(b)
        totalSize++
    }

    @Throws(IOException::class)
    private fun ensureFile(): OutputStream {
        if (fileOutputStream == null) {
            file = Files.createTempFile("tmp", "git-as-svn")
            fileOutputStream = Files.newOutputStream(file)
        }
        return (fileOutputStream)!!
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (closed) throw IOException()
        if (memoryStream.size() < maxMemorySize) {
            val size: Int = min(maxMemorySize - memoryStream.size(), len)
            memoryStream.write(b, off, size)
            if (size < len) {
                ensureFile().write(b, off + size, len - size)
            }
        } else {
            ensureFile().write(b, off, len)
        }
        totalSize += len.toLong()
    }

    @Throws(IOException::class)
    override fun flush() {
        if (closed) throw IOException()
        fileOutputStream?.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        if (closed) return
        closed = true
        try {
            fileOutputStream?.close()
        } finally {
            if (file != null) Files.deleteIfExists(file)
        }
    }

    fun size(): Long {
        return totalSize
    }

    @TestOnly
    fun tempFile(): Path? {
        return file
    }

    @Throws(IOException::class)
    fun toInputStream(): InputStream {
        if (closed) throw IOException()
        if (fileOutputStream != null) flush()
        val result: InputStream = if (file == null) ByteArrayInputStream(memoryStream.toByteArray()) else TemporaryInputStream(memoryStream.toByteArray(), file!!)
        file = null
        close()
        return result
    }

    private class TemporaryInputStream(private val memoryBytes: ByteArray, private val file: Path) : InputStream() {
        private val fileStream: InputStream = Files.newInputStream(file)
        private var offset: Int = 0

        @Throws(IOException::class)
        override fun read(): Int {
            if (offset < memoryBytes.size) {
                return memoryBytes[offset++].toInt() and 0xff
            }
            return fileStream.read()
        }

        @Throws(IOException::class)
        override fun read(buf: ByteArray, off: Int, len: Int): Int {
            if (len == 0) {
                return 0
            }
            if (offset < memoryBytes.size) {
                val count: Int = min(len, memoryBytes.size - offset)
                System.arraycopy(memoryBytes, offset, buf, off, count)
                offset += count
                return count
            }
            return fileStream.read(buf, off, len)
        }

        @Throws(IOException::class)
        override fun close() {
            try {
                fileStream.close()
            } finally {
                Files.deleteIfExists(file)
            }
        }

    }

    companion object {
        const val MAX_MEMORY_SIZE: Int = 8 * 1024 * 1024
    }
}
