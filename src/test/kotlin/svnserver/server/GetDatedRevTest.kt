/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.testng.Assert
import org.testng.annotations.Test
import svnserver.SvnTestServer
import java.util.*

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GetDatedRevTest {
    @Test
    fun simple() {
        SvnTestServer.createEmpty().use { server -> Assert.assertEquals(server.openSvnRepository().getDatedRevision(Date(0)), 0) }
    }
}
