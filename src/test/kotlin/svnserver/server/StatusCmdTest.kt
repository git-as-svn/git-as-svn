/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.testng.annotations.Test
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc2.SvnOperationFactory
import org.tmatesoft.svn.core.wc2.SvnTarget
import svnserver.SvnTestServer

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class StatusCmdTest {
    @Test
    fun simple() {
        SvnTestServer.createMasterRepository().use { server ->
            val factory: SvnOperationFactory = server.createOperationFactory()
            val checkout = factory.createCheckout()
            checkout.source = SvnTarget.fromURL(server.url)
            checkout.setSingleTarget(SvnTarget.fromFile(server.tempDirectory.toFile()))
            checkout.revision = SVNRevision.create(1)
            checkout.run()
            val status = factory.createGetStatus()
            status.setSingleTarget(SvnTarget.fromFile(server.tempDirectory.toFile()))
            status.revision = SVNRevision.create(2)
            status.run()
        }
    }
}
