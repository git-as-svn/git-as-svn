/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay

import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.io.ISVNEditor

/**
 * Filter ISVNEditor events to remove svn:entry properties.
 *
 * @author a.navrotskiy
 */
class FilterSVNEditor(editor: ISVNEditor, checkDelete: Boolean) : SVNEditorWrapper(editor, checkDelete) {
    @Throws(SVNException::class)
    override fun changeDirProperty(name: String, value: SVNPropertyValue) {
        if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            super.changeDirProperty(name, value)
        }
    }

    @Throws(SVNException::class)
    override fun changeFileProperty(path: String, propertyName: String, propertyValue: SVNPropertyValue?) {
        if (!propertyName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            super.changeFileProperty(path, propertyName, propertyValue)
        }
    }
}
