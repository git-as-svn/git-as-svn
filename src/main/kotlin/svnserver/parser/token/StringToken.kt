/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser.token

import svnserver.StringHelper
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Ключевое слово.
 *
 *
 * Бинарная строка или текст известной длины.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class StringToken : TextToken {
    val data: ByteArray

    constructor(data: ByteArray) {
        this.data = data
    }

    constructor(text: String) {
        data = text.toByteArray(StandardCharsets.UTF_8)
    }

    override val text: String
        get() {
            return String(data, StandardCharsets.UTF_8)
        }

    @Throws(IOException::class)
    override fun write(stream: OutputStream) {
        write(stream, data, 0, data.size)
    }

    override fun toString(): String {
        val value: String = if (isUtf(data)) {
            '\"'.toString() + String(data, StandardCharsets.UTF_8) + '\"'
        } else {
            "0x" + StringHelper.toHex(data)
        }
        return "String{$value}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StringToken

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    companion object {
        val empty = StringToken("")

        @Throws(IOException::class)
        fun write(stream: OutputStream, data: ByteArray, offset: Int, length: Int) {
            stream.write(length.toLong().toString(10).toByteArray(StandardCharsets.ISO_8859_1))
            stream.write(':'.code)
            stream.write(data, offset, length)
            stream.write(' '.code)
        }

        private fun isUtf(data: ByteArray): Boolean {
            var i = 0
            while (i < data.size) {
                var continuationBytes: Int = if (data[i] <= 0x7F) 0 else if (data[i] in 0xC0..0xDF /*11011111*/) 1 else if (data[i] in 0xE0..0xEF /*11101111*/) 2 else if (data[i] in 0xF0..0xF4 /* Cause of RFC 3629 */) 3 else return false
                i += 1
                while ((i < data.size) && (continuationBytes > 0
                            ) && (data[i] >= 0x80
                            ) && (data[i] <= 0xBF)
                ) {
                    i += 1
                    continuationBytes -= 1
                }
                if (continuationBytes != 0) return false
            }
            return true
        }
    }
}
