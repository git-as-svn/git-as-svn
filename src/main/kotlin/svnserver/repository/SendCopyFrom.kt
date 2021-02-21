/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository

import svnserver.StringHelper
import svnserver.repository.git.GitFile
import java.io.IOException

/**
 * Send copy from type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
enum class SendCopyFrom {
    /**
     * Never send copy from. Always send full file.
     */
    Never {
        override fun getCopyFrom(basePath: String, file: GitFile): VcsCopyFrom? {
            return null
        }
    },

    /**
     * Always send copy from information. Always send delta with copy-from file.
     */
    Always {
        @Throws(IOException::class)
        override fun getCopyFrom(basePath: String, file: GitFile): VcsCopyFrom? {
            return file.copyFrom
        }
    },

    /**
     * Send copy from information only if file copied from subpath of basePath.
     */
    OnlyRelative {
        @Throws(IOException::class)
        override fun getCopyFrom(basePath: String, file: GitFile): VcsCopyFrom? {
            val copyFrom: VcsCopyFrom? = file.copyFrom
            return if (copyFrom != null && StringHelper.isParentPath(basePath, copyFrom.path)) copyFrom else null
        }
    };

    @Throws(IOException::class)
    abstract fun getCopyFrom(basePath: String, file: GitFile): VcsCopyFrom?
}
