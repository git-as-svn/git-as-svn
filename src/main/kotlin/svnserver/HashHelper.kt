/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Helper methods.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object HashHelper {
    fun md5(): MessageDigest {
        try {
            return MessageDigest.getInstance("MD5")
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        }
    }

    fun sha256(): MessageDigest {
        try {
            return MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        }
    }
}
