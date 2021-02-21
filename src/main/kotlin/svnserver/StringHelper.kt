/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import java.text.SimpleDateFormat
import java.util.*

/**
 * Useful string utilites.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object StringHelper {
    private val DIGITS: CharArray = "0123456789abcdef".toCharArray()
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    fun toHex(data: ByteArray): String {
        val result: StringBuilder = StringBuilder()
        for (i: Byte in data) {
            result.append(DIGITS[i.toInt() shr 4 and 0x0F])
            result.append(DIGITS[i.toInt() and 0x0F])
        }
        return result.toString()
    }

    fun formatDate(time: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        df.timeZone = UTC
        return df.format(Date(time))
    }

    fun joinPath(path: String, localPath: String?): String {
        if (localPath == null || localPath.isEmpty()) {
            return normalize(path)
        }
        if (localPath.startsWith("/")) {
            return normalize(localPath)
        }
        return normalize(path + (if (path.endsWith("/")) "" else "/") + localPath)
    }

    fun normalize(path: String): String {
        if (path.isEmpty()) return ""
        var result: String = path
        if (result[0] != '/') {
            result = "/$result"
        } else if (result.length == 1) {
            return ""
        }
        return if (result.endsWith("/")) result.substring(0, result.length - 1) else result
    }

    fun normalizeDir(path: String): String {
        if (path.isEmpty()) return "/"
        var result: String = path
        if (result[0] != '/') {
            result = "/$result"
        } else if (result.length == 1) {
            return "/"
        }
        return if (result.endsWith("/")) result else "$result/"
    }

    fun parentDir(fullPath: String): String {
        val index: Int = fullPath.lastIndexOf('/')
        return if (index >= 0) fullPath.substring(0, index) else ""
    }

    fun baseName(fullPath: String): String {
        return fullPath.substring(fullPath.lastIndexOf('/') + 1)
    }

    /**
     * Returns true, if parentPath is base path of childPath.
     *
     * @param parentPath Parent path.
     * @param childPath  Child path.
     * @return Returns true, if parentPath is base path of childPath.
     */
    fun isParentPath(parentPath: String, childPath: String): Boolean {
        if (!childPath.startsWith(parentPath)) return false
        val parentLength: Int = parentPath.length
        if (childPath.length == parentLength) return true
        if (childPath[parentLength] == '/') return true
        return parentLength > 0 && childPath[parentLength - 1] == '/'
    }

    /**
     * Get childPath from parentPath or null.
     *
     * @param parentPath    Parent path.
     * @param fullChildPath Full child path.
     * @return Returns child path related parent path or null.
     */
    fun getChildPath(parentPath: String, fullChildPath: String): String? {
        if (parentPath.isEmpty()) {
            if (fullChildPath.length > 1 && fullChildPath[1] == '/') {
                return fullChildPath.substring(1)
            }
            return fullChildPath
        }
        if (fullChildPath.startsWith(parentPath)) {
            val parentLength: Int = parentPath.length
            if (fullChildPath.length == parentLength) return ""
            if (fullChildPath[parentLength] == '/') return fullChildPath.substring(parentLength + 1)
            if (fullChildPath[parentLength - 1] == '/') return fullChildPath.substring(parentLength)
        }
        return null
    }

    fun getFirstLine(message: String?): String? {
        if (message == null) return null
        val eol: Int = message.indexOf('\n')
        return if (eol >= 0) message.substring(0, eol) else message
    }
}
