/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc2.SvnOperationFactory
import org.tmatesoft.svn.core.wc2.SvnTarget
import svnserver.SvnTestHelper
import svnserver.SvnTestServer
import java.nio.file.Files
import java.nio.file.Path

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class WCDepthTest {
    private var server: SvnTestServer? = null
    private var factory: SvnOperationFactory? = null
    private var wc: Path? = null

    @BeforeMethod
    private fun before() {
        server = SvnTestServer.createEmpty()
        val repository = server!!.openSvnRepository()
        factory = server!!.createOperationFactory()
        wc = Files.createDirectories(server!!.tempDirectory.resolve("wc"))
        val editor = repository.getCommitEditor("", null)
        editor.openRoot(-1)
        editor.addDir("/a", null, -1)
        editor.addDir("/a/b", null, -1)
        editor.addFile("/a/b/e", null, -1)
        editor.changeFileProperty("/a/b/e", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
        SvnTestHelper.sendDeltaAndClose(editor, "/a/b/e", null, "e body")
        editor.addDir("/a/b/c", null, -1)
        editor.addFile("/a/b/c/d", null, -1)
        editor.changeFileProperty("/a/b/c/d", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
        SvnTestHelper.sendDeltaAndClose(editor, "/a/b/c/d", null, "d body")
        editor.closeDir()
        editor.closeDir()
        editor.closeDir()
        editor.closeDir()
        editor.closeEdit()
    }

    @Test
    fun empty() {
        checkout("", SVNDepth.EMPTY)
        Assert.assertFalse(Files.exists(wc!!.resolve("a")))
        update("", null)
        Assert.assertFalse(Files.exists(wc!!.resolve("a")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c/d")))
    }
    private fun checkout(path: String, depth: SVNDepth) {
        val checkout = factory!!.createCheckout()
        checkout.source = SvnTarget.fromURL(server!!.url.appendPath(path, true))
        checkout.setSingleTarget(SvnTarget.fromFile(wc!!.toFile()))
        checkout.revision = SVNRevision.HEAD
        checkout.depth = depth
        checkout.run()
    }
    private fun update(path: String, depth: SVNDepth?) {
        val update = factory!!.createUpdate()
        update.setSingleTarget(SvnTarget.fromFile(wc!!.resolve(path).toFile()))
        update.revision = SVNRevision.HEAD
        if (depth != null) {
            update.isDepthIsSticky = true
            update.depth = depth
        }
        update.run()
    }

    @Test
    fun emptySubdir() {
        checkout("a/b", SVNDepth.EMPTY)
        Assert.assertFalse(Files.exists(wc!!.resolve("c")))
        Assert.assertFalse(Files.exists(wc!!.resolve("e")))
        update("", null)
        Assert.assertFalse(Files.exists(wc!!.resolve("c")))
        Assert.assertFalse(Files.exists(wc!!.resolve("e")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("e")))
    }

    @Test
    fun emptySubdir2() {
        checkout("a/b/c", SVNDepth.EMPTY)
        Assert.assertFalse(Files.exists(wc!!.resolve("d")))
        update("", null)
        Assert.assertFalse(Files.exists(wc!!.resolve("d")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("d")))
    }

    @Test
    fun infinity() {
        checkout("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/e")))
        update("", null)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/e")))
    }

    @Test
    fun infinitySubdir() {
        checkout("a", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("b/c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("b/e")))
        update("", null)
        Assert.assertTrue(Files.exists(wc!!.resolve("b/c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("b/e")))
    }

    @Test
    fun files() {
        checkout("a/b", SVNDepth.FILES)
        Assert.assertFalse(Files.exists(wc!!.resolve("c")))
        Assert.assertTrue(Files.exists(wc!!.resolve("e")))
        update("", null)
        Assert.assertFalse(Files.exists(wc!!.resolve("c")))
        Assert.assertTrue(Files.exists(wc!!.resolve("e")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("c/d")))
    }

    @Test
    fun immediates() {
        checkout("a/b", SVNDepth.IMMEDIATES)
        Assert.assertTrue(Files.exists(wc!!.resolve("c")))
        Assert.assertFalse(Files.exists(wc!!.resolve("c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("e")))
        update("", null)
        Assert.assertTrue(Files.exists(wc!!.resolve("c")))
        Assert.assertFalse(Files.exists(wc!!.resolve("c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("e")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("c/d")))
    }

    @Test
    fun complex() {
        checkout("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c/d")))
        update("a/b", SVNDepth.FILES)
        Assert.assertFalse(Files.exists(wc!!.resolve("a/b/c")))
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/e")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c")))
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c/d")))
        update("a/b", SVNDepth.EMPTY)
        Assert.assertFalse(Files.exists(wc!!.resolve("a/b/c")))
        Assert.assertFalse(Files.exists(wc!!.resolve("a/b/e")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c/d")))
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/e")))
        update("a/b", SVNDepth.IMMEDIATES)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c")))
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/e")))
        Assert.assertFalse(Files.exists(wc!!.resolve("a/b/c/d")))
        update("", SVNDepth.INFINITY)
        Assert.assertTrue(Files.exists(wc!!.resolve("a/b/c/d")))
    }

    @AfterMethod
    private fun after() {
        server?.close()
    }
}
