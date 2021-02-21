/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository

import org.tmatesoft.svn.core.SVNException
import java.util.*

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
enum class Depth {
    Unknown {
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitUnknown()
        }

        override fun determineAction(requestedDepth: Depth, directory: Boolean): Action {
            return Action.Skip
        }
    },
    Empty {
        override fun determineAction(requestedDepth: Depth, directory: Boolean): Action {
            if (requestedDepth === Immediates || requestedDepth === Infinity) return Action.Upgrade
            return Action.Skip
        }

        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitEmpty()
        }
    },
    Files {
        override fun determineAction(requestedDepth: Depth, directory: Boolean): Action {
            if (directory) return if (requestedDepth === Immediates || requestedDepth === Infinity) Action.Upgrade else Action.Skip
            return if (requestedDepth === Empty) Action.Skip else Action.Normal
        }

        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitFiles()
        }
    },
    Immediates {
        override fun determineAction(requestedDepth: Depth, directory: Boolean): Action {
            if (directory) return if (requestedDepth === Empty || requestedDepth === Files) Action.Skip else Action.Normal
            return if (requestedDepth === Empty) Action.Skip else Action.Normal
        }

        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitImmediates()
        }
    },
    Infinity {
        override fun determineAction(requestedDepth: Depth, directory: Boolean): Action {
            if (directory) return if (requestedDepth === Empty || requestedDepth === Files) Action.Skip else Action.Normal
            return if (requestedDepth === Empty) Action.Skip else Action.Normal
        }

        @Throws(SVNException::class)
        override fun <R> visit(visitor: DepthVisitor<R>): R {
            return visitor.visitInfinity()
        }
    };

    private val value: String = name.toLowerCase(Locale.ENGLISH)

    @Throws(SVNException::class)
    abstract fun <R> visit(visitor: DepthVisitor<R>): R
    abstract fun determineAction(requestedDepth: Depth, directory: Boolean): Action
    fun deepen(): Depth {
        return if (this === Immediates) Empty else this
    }

    enum class Action {
        // Ignore this entry (it's either below the requested depth, or
        // if the requested depth is svn_depth_unknown, below the working
        // copy depth)
        Skip,  // Handle the entry as if it were a newly added repository path

        // (the client is upgrading to a deeper wc and doesn't currently
        // have this entry, but it should be there after the upgrade, so we
        // need to send the whole thing, not just deltas)
        Upgrade,  // Handle this entry normally
        Normal
    }

    companion object {
        fun parse(value: String, recurse: Boolean, nonRecurse: Depth): Depth {
            if (value.isEmpty()) return if (recurse) Infinity else nonRecurse
            return parse(value)
        }

        fun parse(value: String): Depth {
            for (depth: Depth in values()) if ((depth.value == value)) return depth
            return Unknown
        }
    }
}
