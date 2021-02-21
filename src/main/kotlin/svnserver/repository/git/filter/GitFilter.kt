/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter

import org.eclipse.jgit.lib.ObjectId
import svnserver.auth.User
import svnserver.repository.git.GitObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * File fiter interface.
 * Used for implementing "clean" and "smudge" git filter functionality (https://git-scm.com/book/en/v2/Customizing-Git-Git-Attributes).
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface GitFilter {
    /**
     * Get object content hash.
     *
     * @param objectId Object reference.
     * @return Object content hash.
     */
    fun getContentHash(objectId: GitObject<out ObjectId>): String {
        return name + " " + objectId.`object`.name
    }

    /**
     * Filter name.
     *
     * @return Filter name.
     */
    val name: String

    /**
     * Get object md5 sum.
     *
     * @param objectId Object reference.
     * @return Object md5 sum.
     */
    @Throws(IOException::class)
    fun getMd5(objectId: GitObject<out ObjectId>): String

    /**
     * Get object size.
     *
     * @param objectId Object reference.
     * @return Object size in bytes.
     */
    @Throws(IOException::class)
    fun getSize(objectId: GitObject<out ObjectId>): Long

    /**
     * Get object stream.
     *
     * @param objectId Object reference.
     * @return Object stream.
     */
    @Throws(IOException::class)
    fun inputStream(objectId: GitObject<out ObjectId>): InputStream

    /**
     * Create stream wrapper for object.
     *
     * @param stream Stream with real blob data.
     * @param user   User information.
     * @return Return output stream for writing original file data.
     */
    @Throws(IOException::class)
    fun outputStream(stream: OutputStream, user: User): OutputStream
}
