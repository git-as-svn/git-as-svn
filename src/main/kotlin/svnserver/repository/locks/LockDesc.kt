/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks

import ru.bozaro.gitlfs.common.data.Lock
import ru.bozaro.gitlfs.common.data.User
import svnserver.StringHelper
import svnserver.repository.git.GitBranch
import java.util.*

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LockDesc(path: String, val branch: String?, val hash: String?, val token: String, val owner: String?, val comment: String?, val created: Long) {
    val path: String = if (path.startsWith("/")) path else "/$path"

    constructor(path: String, branch: GitBranch?, hash: String?, token: String, owner: String?, comment: String?, created: Long) : this(path, branch?.shortBranchName, hash, token, owner, comment, created)

    val createdString: String
        get() {
            return StringHelper.formatDate(created)
        }

    override fun hashCode(): Int {
        return Objects.hash(path, branch, token, owner, comment, hash, created)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LockDesc) return false
        val lockDesc: LockDesc = other
        // TODO: https://github.com/go-gitea/gitea/issues/7871
        return (// created == lockDesc.created &&
                ((path == lockDesc.path) &&
                        Objects.equals(branch, lockDesc.branch) && (token == lockDesc.token) &&
                        Objects.equals(owner, lockDesc.owner) &&
                        Objects.equals(comment, lockDesc.comment) &&
                        Objects.equals(hash, lockDesc.hash)))
    }

    override fun toString(): String {
        return ("LockDesc{" +
                "path='" + path + '\'' +
                ", branch='" + branch + '\'' +
                ", token='" + token + '\'' +
                ", owner='" + owner + '\'' +
                ", comment='" + comment + '\'' +
                ", hash='" + hash + '\'' +
                ", created=" + created +
                '}')
    }

    companion object {
        fun toLockDesc(lock: Lock): LockDesc {
            val path: String = "/" + lock.path
            return LockDesc(path, null as String?, null, lock.id, if (lock.owner == null) null else lock.owner!!.name, null, lock.lockedAt.time)
        }

        fun toLock(lockDesc: LockDesc): Lock {
            val path: String = toLfsPath(lockDesc.path)
            return Lock(lockDesc.token, path, Date(lockDesc.created), if (lockDesc.owner == null) null else User(lockDesc.owner))
        }

        fun toLfsPath(path: String?): String {
            if (path == null) return ""
            if (path.startsWith("/")) return path.substring(1)
            return path
        }
    }
}
