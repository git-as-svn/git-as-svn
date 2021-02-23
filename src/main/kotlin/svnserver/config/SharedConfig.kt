/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config

import svnserver.context.SharedContext

/**
 * Shared between repositories configuration
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
fun interface SharedConfig {
    /**
     * Add shared data to context.
     *
     * @param context Shared context.
     */
    @Throws(Exception::class)
    fun create(context: SharedContext)
}
