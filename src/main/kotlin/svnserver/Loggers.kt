/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
object Loggers {
    val git: Logger = LoggerFactory.getLogger("git")
    val gitea: Logger = LoggerFactory.getLogger("gitea")
    val gitlab: Logger = LoggerFactory.getLogger("gitlab")
    val ldap: Logger = LoggerFactory.getLogger("ldap")
    val lfs: Logger = LoggerFactory.getLogger("lfs")
    val misc: Logger = LoggerFactory.getLogger("misc")
    val svn: Logger = LoggerFactory.getLogger("svn")
    val web: Logger = LoggerFactory.getLogger("web")
}
