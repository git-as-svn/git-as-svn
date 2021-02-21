/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server

import svnserver.context.Local
import svnserver.context.LocalContext
import svnserver.ext.gitlfs.storage.LfsStorage

/**
 * LFS server entry point.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LfsServerEntry(private val server: LfsServer, private val context: LocalContext, storage: LfsStorage) : Local {
    init {
        server.register(context, storage)
    }

    override fun close() {
        server.unregister(context)
    }
}
