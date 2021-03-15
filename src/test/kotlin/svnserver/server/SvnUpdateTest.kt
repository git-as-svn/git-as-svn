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
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc2.SvnOperationFactory
import org.tmatesoft.svn.core.wc2.SvnTarget
import svnserver.SvnTestServer
import svnserver.TestHelper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Simple update tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnUpdateTest {
    /**
     * Bug: svn up doesnt remove file #18
     * <pre>
     * bozaro@landfill:/tmp/test/git-as-svn$ echo > test.txt
     * bozaro@landfill:/tmp/test/git-as-svn$ svn add test.txt
     * A         test.txt
     * bozaro@landfill:/tmp/test/git-as-svn$ svn commit -m "Add new file"
     * Добавляю          test.txt
     * Передаю данные .
     * Committed revision 58.
     * bozaro@landfill:/tmp/test/git-as-svn$ svn up -r 57
     * Updating '.':
     * В редакции 57.
     * bozaro@landfill:/tmp/test/git-as-svn$ ls -l test.txt
     * -rw-rw-r-- 1 bozaro bozaro 1 авг.  15 00:50 test.txt
     * bozaro@landfill:/tmp/test/git-as-svn$
    </pre> *
     */
    @Test
    fun addAndUpdate() {
        SvnTestServer.createEmpty().use { server ->
            val factory: SvnOperationFactory = server.createOperationFactory()
            val client = SVNClientManager.newInstance(factory)
            // checkout
            val checkout = factory.createCheckout()
            checkout.source = SvnTarget.fromURL(server.url)
            checkout.setSingleTarget(SvnTarget.fromFile(server.tempDirectory.toFile()))
            checkout.revision = SVNRevision.HEAD
            val revision = checkout.run()
            // create file
            val newFile: Path = server.tempDirectory.resolve("somefile.txt")
            TestHelper.saveFile(newFile, "Bla Bla Bla")
            // add file
            client.wcClient.doAdd(newFile.toFile(), false, false, false, SVNDepth.INFINITY, false, true)
            // set eof property
            client.wcClient.doSetProperty(newFile.toFile(), SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE), false, SVNDepth.INFINITY, null, null)
            // commit new file
            client.commitClient.doCommit(arrayOf(newFile.toFile()), false, "Add file commit", null, null, false, false, SVNDepth.INFINITY)
            // update for checkout revision
            client.updateClient.doUpdate(server.tempDirectory.toFile(), SVNRevision.create(revision), SVNDepth.INFINITY, false, false)
            // file must be remove
            Assert.assertFalse(Files.exists(newFile))
        }
    }
}
