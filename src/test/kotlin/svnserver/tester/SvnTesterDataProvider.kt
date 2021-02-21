/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.tester

import org.testng.annotations.DataProvider
import svnserver.SvnTestServer
import java.util.*

/**
 * Provider for creating tests for compare with reference svn server implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnTesterDataProvider {
    @DataProvider
    fun all(): Array<Array<out Any>> {
        return createTesters().map { arrayOf(it) }.toTypedArray()
    }

    private fun createTesters(): Array<NamedFactory> {
        val result = ArrayList<NamedFactory>()
        val external = SvnTesterExternalListener.get()
        if (external != null) {
            result.add(NamedFactory("Native", external))
        }
        result.add(NamedFactory("GitAsSvn") { SvnTestServer.createEmpty() })
        result.add(NamedFactory("SvnKit") { SvnTesterSvnKit() })
        return result.toTypedArray()
    }

    class NamedFactory(private val name: String, private val factory: SvnTesterFactory) : SvnTesterFactory {
        @Throws(Exception::class)
        override fun create(): SvnTester {
            return factory.create()
        }

        override fun toString(): String {
            return name
        }
    }
}
