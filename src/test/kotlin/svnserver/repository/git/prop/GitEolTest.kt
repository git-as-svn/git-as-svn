/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.attributes.Attributes
import org.eclipse.jgit.attributes.AttributesRule
import org.eclipse.jgit.lib.FileMode
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import svnserver.TestHelper
import svnserver.repository.git.RepositoryFormat
import svnserver.repository.git.prop.GitAttributesFactory.EolType
import java.util.*

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitEolTest {
    @DataProvider
    fun eolTypeV5Data(): Array<Array<out Any>> {
        return arrayOf(
            arrayOf("", EolType.Autodetect),

            arrayOf("crlf", EolType.Native), // Be careful here
            arrayOf("-crlf", EolType.Binary),
            arrayOf("crlf=input", EolType.LF),

            arrayOf("eol=lf", EolType.LF),
            arrayOf("eol=crlf", EolType.CRLF),

            arrayOf("eol=lf text=auto", EolType.Autodetect),
            arrayOf("eol=crlf text=auto", EolType.Autodetect),

            arrayOf("text", EolType.Native),
            arrayOf("-text", EolType.Binary),
            arrayOf("text=auto", EolType.Autodetect),

            // invalid values
            arrayOf("crlf=invalid", EolType.Autodetect),
            arrayOf("eol=invalid", EolType.Autodetect),
            arrayOf("eol", EolType.Autodetect),
            arrayOf("text=invalid", EolType.Autodetect),
        )
    }

    @Test(dataProvider = "eolTypeV5Data")
    fun eolTypeV5(rule: String, expected: EolType) {
        val attrs = Attributes(*AttributesRule("", rule).attributes.toTypedArray())
        val actual = GitAttributesFactory.getEolTypeV5(attrs)
        Assert.assertEquals(actual, expected)
    }

    @DataProvider
    fun eolTypeV4Data(): Array<Array<out Any>> {
        return arrayOf(
            arrayOf("", EolType.Autodetect),

            arrayOf("eol=lf", EolType.LF),
            arrayOf("eol=crlf", EolType.CRLF),

            arrayOf("text", EolType.Native),
            arrayOf("-text", EolType.Binary),
            arrayOf("text=auto", EolType.Native),

            // invalid values
            arrayOf("eol=invalid", EolType.Autodetect),
            arrayOf("eol", EolType.Autodetect),
            arrayOf("text=invalid", EolType.Native),
        )
    }

    @Test(dataProvider = "eolTypeV4Data")
    fun eolTypeV4(rule: String, expected: EolType) {
        val attrs = Attributes(*AttributesRule("", rule).attributes.toTypedArray())
        val actual = GitAttributesFactory.getEolTypeV4(attrs)
        Assert.assertEquals(actual, expected)
    }

    @Test(dataProvider = "parseAttributesData")
    fun parseAttributes(params: Params) {
        params.check()
    }

    class Params internal constructor(private val attr: Array<GitProperty>, private val path: String) {
        private val expected = TreeMap<String, String>()
        fun prop(key: String, value: String): Params {
            expected[key] = value
            return this
        }

        override fun toString(): String {
            return path
        }

        fun check() {
            val gitProperties = createForPath(attr, path)
            val svnProperties = HashMap<String, String>()
            for (prop in gitProperties) {
                prop.apply(svnProperties)
            }
            for ((key, value) in expected) {
                Assert.assertEquals(svnProperties[key], value, key)
            }
            for ((key, value) in svnProperties) {
                Assert.assertEquals(value, expected[key], key)
            }
        }
    }

    companion object {
        @JvmStatic
        @DataProvider(name = "parseAttributesData")
        fun parseAttributesData(): Array<Array<out Any>> {
            val attr: Array<GitProperty>
            TestHelper.asStream(
                """
                    # comment
                    *     text
                    *.txt text
                    *.md  eol=lf
                    *.dat -text
                    3.md -text
                    *.bin binary
                    1.bin text
                    2.bin text
                    
                    """.trimIndent()
            ).use { `in` -> attr = GitAttributesFactory().create(`in`, RepositoryFormat.Latest) }
            val params = arrayOf(
                Params(attr, "/").prop(
                    SVNProperty.INHERITABLE_AUTO_PROPS, """
     *.txt = svn:eol-style=native
     *.md = svn:eol-style=LF
     *.dat = svn:mime-type=application/octet-stream
     3.md = svn:mime-type=application/octet-stream
     *.bin = svn:mime-type=application/octet-stream
     1.bin = svn:eol-style=native
     2.bin = svn:eol-style=native
     
     """.trimIndent()
                ), Params(attr, "README.md").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_LF), Params(attr, "foo.dat").prop(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE), Params(attr, "foo.txt").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE), Params(attr, "foo.bin").prop(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE), Params(attr, "1.bin").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE), Params(attr, "2.bin").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE), Params(attr, "3.md").prop(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE), Params(attr, "changelog").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE)
            )
            return params.map { arrayOf(it) }.toTypedArray()
        }

        private fun createForPath(baseProps: Array<GitProperty>, path: String): Array<GitProperty> {
            var props = baseProps
            val pathItems = path.split("/".toRegex()).toTypedArray()
            for (i in pathItems.indices) {
                val name = pathItems[i]
                if (name.isNotEmpty()) {
                    val mode = if (i == pathItems.size - 1) FileMode.REGULAR_FILE else FileMode.TREE
                    props = createForChild(props, name, mode)
                }
            }
            return props
        }

        private fun createForChild(props: Array<GitProperty>, name: String, mode: FileMode): Array<GitProperty> {
            val result = arrayOfNulls<GitProperty>(props.size)
            var count = 0
            for (prop in props) {
                val child = prop.createForChild(name, mode)
                if (child != null) {
                    result[count++] = child
                }
            }
            return Arrays.copyOf(result, count)
        }
    }
}
