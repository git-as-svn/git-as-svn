/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import svnserver.StringHelper
import svnserver.repository.SvnForbiddenException
import java.io.IOException
import java.util.*

/**
 * Class for collecting changes in revision.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal object ChangeHelper {
    @Throws(IOException::class)
    fun collectChanges(oldTree: GitFile?, newTree: GitFile, fullRemoved: Boolean, stringInterner: (String) -> String): Map<String, GitLogEntry> {
        val changes = HashMap<String, GitLogEntry>()
        val logEntry = GitLogEntry(oldTree, newTree)
        if (oldTree == null || logEntry.isModified) {
            changes["/"] = logEntry
        }
        val queue = ArrayDeque<TreeCompareEntry>()
        queue.add(TreeCompareEntry("", oldTree, newTree))
        while (!queue.isEmpty()) {
            collectChanges(changes, queue, queue.remove(), fullRemoved, stringInterner)
        }
        return changes
    }

    @Throws(IOException::class)
    private fun collectChanges(changes: MutableMap<String, GitLogEntry>, queue: Queue<TreeCompareEntry>, compareEntry: TreeCompareEntry, fullRemoved: Boolean, stringInterner: (String) -> String) {
        for (pair: GitLogEntry in compareEntry) {
            val newEntry: GitFile? = pair.newEntry
            val oldEntry: GitFile? = pair.oldEntry
            if (newEntry == null && oldEntry == null) {
                throw IllegalStateException()
            }
            if (newEntry != null) {
                if (newEntry != oldEntry) {
                    val fullPath: String = stringInterner(StringHelper.joinPath(compareEntry.path, newEntry.fileName))
                    if (newEntry.isDirectory) {
                        val oldChange: GitLogEntry? = changes.put(fullPath, pair)
                        if (oldChange != null) {
                            changes[fullPath] = GitLogEntry(oldChange.oldEntry, newEntry)
                        }
                        queue.add(TreeCompareEntry(fullPath, if (((oldEntry != null) && oldEntry.isDirectory)) oldEntry else null, newEntry))
                    } else if (oldEntry == null || pair.isModified) {
                        val oldChange: GitLogEntry? = changes.put(fullPath, pair)
                        if (oldChange != null) {
                            changes[fullPath] = GitLogEntry(oldChange.oldEntry, newEntry)
                        }
                    }
                }
            } else {
                val fullPath: String = StringHelper.joinPath(compareEntry.path, oldEntry!!.fileName)
                val oldChange: GitLogEntry? = changes.put(fullPath, pair)
                if (oldChange != null) {
                    changes[fullPath] = GitLogEntry(oldEntry, oldChange.newEntry)
                }
            }
            if (fullRemoved && (oldEntry != null) && oldEntry.isDirectory) {
                val fullPath: String = StringHelper.joinPath(compareEntry.path, oldEntry.fileName)
                if (newEntry == null || (!newEntry.isDirectory)) {
                    queue.add(TreeCompareEntry(fullPath, oldEntry, null))
                }
            }
        }
    }

    private class TreeCompareEntry(val path: String, oldTree: GitFile?, newTree: GitFile?) : Iterable<GitLogEntry?> {
        private val oldTree: Iterable<GitFile> = getIterable(oldTree)
        private val newTree: Iterable<GitFile> = getIterable(newTree)
        override fun iterator(): Iterator<GitLogEntry> {
            return LogPairIterator(oldTree, newTree)
        }

        companion object {
            @Throws(IOException::class)
            private fun getIterable(tree: GitFile?): Iterable<GitFile> {
                return try {
                    tree?.entries ?: emptyList()
                } catch (e: SvnForbiddenException) {
                    // todo: Need some additional logic for missing Git objects
                    emptyList()
                }
            }
        }

    }

    private class LogPairIterator(oldTree: Iterable<GitFile>, newTree: Iterable<GitFile>) : Iterator<GitLogEntry> {
        private val oldIter: Iterator<GitFile> = oldTree.iterator()
        private val newIter: Iterator<GitFile> = newTree.iterator()
        private var oldItem: GitFile?
        private var newItem: GitFile?

        override fun hasNext(): Boolean {
            return (oldItem != null) || (newItem != null)
        }

        override fun next(): GitLogEntry {
            val compare: Int = when {
                newItem == null -> {
                    -1
                }
                oldItem == null -> {
                    1
                }
                else -> {
                    val oldTreeEntry: GitTreeEntry? = oldItem!!.treeEntry
                    val newTreeEntry: GitTreeEntry? = newItem!!.treeEntry
                    if (oldTreeEntry == null || newTreeEntry == null) {
                        throw IllegalStateException("Tree entry can be null only for revision tree root.")
                    }
                    oldTreeEntry.compareTo(newTreeEntry)
                }
            }
            val oldEntry: GitFile?
            val newEntry: GitFile?
            if (compare <= 0) {
                oldEntry = oldItem
                oldItem = nextItem(oldIter)
            } else {
                oldEntry = null
            }
            if (compare >= 0) {
                newEntry = newItem
                newItem = nextItem(newIter)
            } else {
                newEntry = null
            }
            return GitLogEntry(oldEntry, newEntry)
        }

        companion object {
            private fun nextItem(iter: Iterator<GitFile>): GitFile? {
                return if (iter.hasNext()) iter.next() else null
            }
        }

        init {
            oldItem = nextItem(oldIter)
            newItem = nextItem(newIter)
        }
    }
}
