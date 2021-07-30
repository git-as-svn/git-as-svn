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

/**
 * Конец списка.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class ListEndToken private constructor() : SvnServerToken {
    @Throws(IOException::class)
    override fun write(stream: OutputStream) {
        stream.write(TOKEN)
    }

    override fun toString(): String {
        return "ListEnd"
    }

    companion object {
        val instance: ListEndToken = ListEndToken()
        private val TOKEN: ByteArray = byteArrayOf(')'.code.toByte(), ' '.code.toByte())
    }
}
