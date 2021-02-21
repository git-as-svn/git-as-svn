/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config

import svnserver.config.ConfigHelper
import svnserver.context.LocalContext
import svnserver.ext.gitlfs.LocalLfsConfig.LfsLayout
import svnserver.ext.gitlfs.storage.local.LfsLocalReader

class FileLfsMode : LfsMode {
    private var path = "/var/opt/gitlab/gitlab-rails/shared/lfs-objects"
    override fun readerFactory(context: LocalContext): LfsReaderFactory {
        val dataRoot = ConfigHelper.joinPath(context.shared.basePath, path)
        return LfsReaderFactory { oid: String -> LfsLocalReader.create(LfsLayout.GitLab, dataRoot, null, oid) }
    }
}
