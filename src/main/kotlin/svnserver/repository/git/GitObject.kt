/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.Repository
import java.io.IOException

/**
 * Git object with repository information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitObject<T : ObjectId>(val repo: Repository, val `object`: T) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val gitObject: GitObject<*> = other as GitObject<*>
        return AnyObjectId.isEqual(`object`, gitObject.`object`)
    }

    @Throws(IOException::class)
    fun openObject(): ObjectLoader {
        return repo.newObjectReader().open(`object`)
    }

    override fun hashCode(): Int {
        return `object`.hashCode()
    }

    override fun toString(): String {
        return `object`.name()
    }
}
