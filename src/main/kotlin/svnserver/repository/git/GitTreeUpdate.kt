/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.ObjectChecker
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectInserter
import org.eclipse.jgit.lib.TreeFormatter
import java.io.IOException
import java.util.*

/**
 * Git tree updater.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitTreeUpdate constructor(val name: String, entries: Iterable<GitTreeEntry>) {
    val entries: MutableMap<String, GitTreeEntry>

    @Throws(IOException::class)
    fun buildTree(inserter: ObjectInserter): ObjectId {
        val treeBuilder = TreeFormatter()
        val sortedEntries = entries.values.toMutableList().sorted()
        for (entry: GitTreeEntry in sortedEntries) {
            treeBuilder.append(entry.fileName, entry.fileMode, entry.objectId.`object`)
        }
        ObjectChecker().checkTree(treeBuilder.toByteArray())
        return inserter.insert(treeBuilder)
    }

    init {
        this.entries = HashMap()
        for (entry: GitTreeEntry in entries) {
            this.entries[entry.fileName] = entry
        }
    }
}
