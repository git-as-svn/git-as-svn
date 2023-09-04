/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser.token

import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Ключевое слово.
 *
 *
 * Допустимые символы: 'a'..'z', 'A'..'Z', '-', '0'..'9'
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class WordToken(override val text: String) : TextToken {
    @Throws(IOException::class)
    override fun write(stream: OutputStream) {
        write(stream, text)
    }

    override fun toString(): String {
        return "Word{$text}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WordToken

        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        return text.hashCode()
    }

    companion object {
        val empty = WordToken("")

        @Throws(IOException::class)
        fun write(stream: OutputStream, word: String) {
            stream.write(word.toByteArray(StandardCharsets.US_ASCII))
            stream.write(' '.code)
        }
    }
}
