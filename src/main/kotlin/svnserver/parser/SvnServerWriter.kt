/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser

import svnserver.parser.token.*
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Интерфейс для записи данных в поток.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnServerWriter(stream: OutputStream, bufferSize: Int = SvnServerParser.DEFAULT_BUFFER_SIZE) : Closeable {

    private val stream = BufferedOutputStream(stream, bufferSize)
    private var depth: Int = 0

    @Throws(IOException::class)
    fun listBegin(): SvnServerWriter {
        return write(ListBeginToken.instance)
    }

    @Throws(IOException::class)
    fun listEnd(): SvnServerWriter {
        return write(ListEndToken.instance)
    }

    @Throws(IOException::class)
    fun word(c: Char): SvnServerWriter {
        return word(c.toString())
    }

    @Throws(IOException::class)
    fun word(word: String): SvnServerWriter {
        WordToken.write(stream, word)
        if (depth == 0) stream.flush()
        return this
    }

    @Throws(IOException::class)
    fun stringNullable(text: String?): SvnServerWriter {
        listBegin()
        if (text != null) string(text)
        listEnd()
        return this
    }

    @Throws(IOException::class)
    fun string(text: String): SvnServerWriter {
        return binary(text.toByteArray(StandardCharsets.UTF_8))
    }

    @Throws(IOException::class)
    fun binary(data: ByteArray, offset: Int = 0, length: Int = data.size): SvnServerWriter {
        StringToken.write(stream, data, offset, length)
        if (depth == 0) stream.flush()
        return this
    }

    @Throws(IOException::class)
    fun number(number: Long): SvnServerWriter {
        NumberToken.write(stream, number)
        if (depth == 0) stream.flush()
        return this
    }

    @Throws(IOException::class)
    fun separator(): SvnServerWriter {
        stream.write('\n'.code)
        return this
    }

    @Throws(IOException::class)
    fun bool(value: Boolean): SvnServerWriter {
        return word(if (value) "true" else "false")
    }

    @Throws(IOException::class)
    fun write(token: SvnServerToken): SvnServerWriter {
        token.write(stream)
        if ((token == ListBeginToken.instance)) {
            depth++
        } else if ((token == ListEndToken.instance)) {
            depth--
            if (depth < 0) {
                throw IllegalStateException("Too many closed lists.")
            }
        }
        if (depth == 0) {
            separator()
            stream.flush()
        }
        return this
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun writeMap(properties: Map<String, String?>?, nullableValues: Boolean = false): SvnServerWriter {
        listBegin()
        if (properties != null) {
            for (entry: Map.Entry<String, String?> in properties.entries) {
                listBegin()
                string(entry.key)
                if (nullableValues) {
                    stringNullable(entry.value)
                } else {
                    string(entry.value!!)
                }
                listEnd()
            }
        }
        listEnd()
        return this
    }

    @Throws(IOException::class)
    override fun close() {
        stream.use { if (depth != 0) throw IllegalStateException("Unmatched parentheses") }
    }
}
