/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks

import svnserver.StringHelper
import svnserver.repository.DepthVisitor
import java.util.*

/**
 * Depth visitor for lock iteration.
 * *
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class TreeMapLockDepthVisitor internal constructor(private val locks: SortedMap<String, LockDesc>, private val pathKey: String) : DepthVisitor<Iterator<LockDesc>> {
    override fun visitEmpty(): Iterator<LockDesc> {
        val desc: LockDesc? = locks[pathKey]
        return if (desc == null) Collections.emptyIterator() else arrayOf(desc).iterator()
    }

    override fun visitFiles(): Iterator<LockDesc> {
        return object : LockDescIterator(locks, pathKey) {
            override fun filter(item: Map.Entry<String, LockDesc>): Boolean {
                return pathKey == item.key || pathKey == StringHelper.parentDir(item.key)
            }
        }
    }

    override fun visitImmediates(): Iterator<LockDesc> {
        return visitFiles()
    }

    override fun visitInfinity(): Iterator<LockDesc> {
        return object : LockDescIterator(locks, pathKey) {
            override fun filter(item: Map.Entry<String, LockDesc>): Boolean {
                return true
            }
        }
    }

    override fun visitUnknown(): Iterator<LockDesc> {
        return Collections.emptyIterator()
    }

    private abstract class LockDescIterator(locks: SortedMap<String, LockDesc>, pathKey: String) : Iterator<LockDesc> {
        private val iterator: Iterator<Map.Entry<String, LockDesc>>
        private val pathKey: String
        private var nextItem: LockDesc?
        private fun findNext(): LockDesc? {
            while (iterator.hasNext()) {
                val item: Map.Entry<String, LockDesc> = iterator.next()
                if (StringHelper.isParentPath(pathKey, item.key)) {
                    if (filter(item)) {
                        return item.value
                    }
                }
            }
            return null
        }

        protected abstract fun filter(item: Map.Entry<String, LockDesc>): Boolean
        override fun hasNext(): Boolean {
            return nextItem != null
        }

        override fun next(): LockDesc {
            val result: LockDesc? = nextItem
            if (result != null) {
                nextItem = findNext()
            }
            return result!!
        }

        init {
            iterator = locks.tailMap(pathKey).entries.iterator()
            this.pathKey = pathKey
            nextItem = findNext()
        }
    }
}
