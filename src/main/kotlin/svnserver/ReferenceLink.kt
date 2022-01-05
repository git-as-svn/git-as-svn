/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

/**
 * Helper for create documentation links.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
enum class ReferenceLink(private val anchor: String) {
    InvalidSvnProps("invalid-svn-props");

    val link: String
        get() {
            return "$BASE_URL#$anchor"
        }

    companion object {
        const val BASE_URL: String = "https://git-as-svn.github.io/git-as-svn/htmlsingle/git-as-svn.html"
    }
}
