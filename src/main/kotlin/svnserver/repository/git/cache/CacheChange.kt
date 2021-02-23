/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.cache

import org.eclipse.jgit.lib.ObjectId
import svnserver.repository.git.GitFile
import svnserver.repository.git.GitLogEntry

/**
 * Change file/directory information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class CacheChange {
    val oldFile: ObjectId?
    val newFile: ObjectId?

    constructor() {
        oldFile = null
        newFile = null
    }

    constructor(logPair: GitLogEntry) : this(getFileId(logPair.oldEntry), getFileId(logPair.newEntry))
    constructor(oldFile: ObjectId?, newFile: ObjectId?) {
        this.oldFile = oldFile?.copy()
        this.newFile = newFile?.copy()
    }

    companion object {
        private fun getFileId(gitFile: GitFile?): ObjectId? {
            return gitFile?.objectId?.`object`
        }
    }
}
