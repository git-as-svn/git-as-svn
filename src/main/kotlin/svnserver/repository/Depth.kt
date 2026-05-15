/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository

import org.tmatesoft.svn.core.SVNException

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
enum class Depth {
    Empty {
        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitEmpty()
        }

        override fun deepen(directory: Boolean): Depth? {
            return null
        }
    },

    Files {
        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitFiles()
        }

        override fun deepen(directory: Boolean): Depth? {
            return if (directory) null else Immediates
        }
    },

    Immediates {
        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitImmediates()
        }

        override fun deepen(directory: Boolean): Depth? {
            return Empty
        }
    },

    Infinity {
        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitInfinity()
        }

        override fun deepen(directory: Boolean): Depth? {
            return Infinity
        }
    };

    private val value: String = name.lowercase()

    @Throws(SVNException::class)
    abstract fun <R> visit(visitor: DepthVisitor<R>): R

    abstract fun deepen(directory: Boolean): Depth?

    companion object {
        fun parse(value: String, recurse: Boolean, nonRecurse: Depth): Depth? {
            if (value.isEmpty())
                return if (recurse) Infinity else nonRecurse

            return parse(value)
        }

        fun parse(value: String): Depth? {
            for (depth: Depth in entries)
                if (depth.value == value)
                    return depth

            return null
        }
    }
}
