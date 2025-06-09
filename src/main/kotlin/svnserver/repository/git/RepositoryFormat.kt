/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

enum class RepositoryFormat(val revision: Int) {
    V4(4),
    V5_REMOVE_IMPLICIT_NATIVE_EOL(5),
    Latest(V5_REMOVE_IMPLICIT_NATIVE_EOL.revision),
}
