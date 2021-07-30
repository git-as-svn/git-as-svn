/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser.token

import svnserver.parser.SvnServerToken
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Число.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class NumberToken constructor(val number: Int) : SvnServerToken {
    @Throws(IOException::class)
    override fun write(stream: OutputStream) {
        write(stream, number.toLong())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NumberToken

        if (number != other.number) return false

        return true
    }

    override fun hashCode(): Int {
        return number
    }

    override fun toString(): String {
        return "NumberToken(number=$number)"
    }

    companion object {
        @Throws(IOException::class)
        fun write(stream: OutputStream, number: Long) {
            stream.write(number.toString().toByteArray(StandardCharsets.ISO_8859_1))
            stream.write(' '.code)
        }
    }
}
