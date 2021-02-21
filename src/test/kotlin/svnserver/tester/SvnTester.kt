/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.tester

import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.io.SVNRepository

/**
 * Interface for testing subversion server,
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface SvnTester : AutoCloseable {
    /**
     * Get URL to root of the working copy.
     *
     * @return Working copy root.
     * @throws SVNException Some error.
     */
    @get:Throws(SVNException::class)
    val url: SVNURL

    /**
     * Open connection to subversion server.
     *
     * @return New connection.
     * @throws SVNException Some error.
     */
    @Throws(SVNException::class)
    fun openSvnRepository(): SVNRepository
}
