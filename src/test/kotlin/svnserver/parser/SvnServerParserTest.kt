/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser

import org.testng.*
import org.testng.annotations.*
import org.testng.internal.junit.ArrayAsserts
import svnserver.parser.MessageParser.parse
import svnserver.parser.token.*
import svnserver.server.msg.ClientInfo
import java.io.*
import java.nio.charset.StandardCharsets

/**
 * Тесты для проверки парсера.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnServerParserTest {
    @Test
    fun testSimpleParse() {
        ByteArrayInputStream("( word 22 10:string 1:x 1:  8:Тест ( sublist ) ) ".toByteArray(StandardCharsets.UTF_8)).use { stream ->
            val parser = SvnServerParser(stream)
            Assert.assertEquals(parser.readToken(ListBeginToken::class.java), ListBeginToken.instance)
            Assert.assertEquals(parser.readToken(WordToken::class.java), WordToken("word"))
            Assert.assertEquals(parser.readToken(NumberToken::class.java), NumberToken(22))
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken("string 1:x"))
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken(" "))
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken("Тест"))
            Assert.assertEquals(parser.readToken(ListBeginToken::class.java), ListBeginToken.instance)
            Assert.assertEquals(parser.readToken(WordToken::class.java), WordToken("sublist"))
            Assert.assertEquals(parser.readToken(ListEndToken::class.java), ListEndToken.instance)
            Assert.assertEquals(parser.readToken(ListEndToken::class.java), ListEndToken.instance)
            try {
                parser.readToken(ListEndToken::class.java)
                Assert.fail()
            } catch (ignored: EOFException) {
            }
        }
    }

    @Test
    fun testSimpleParseSmallBuffer() {
        ByteArrayInputStream("( word 22 10:string 1:x 1:  8:Тест ( sublist ) ) ".toByteArray(StandardCharsets.UTF_8)).use { stream ->
            val parser = SvnServerParser(stream, 10)
            Assert.assertEquals(parser.readToken(ListBeginToken::class.java), ListBeginToken.instance)
            Assert.assertEquals(parser.readToken(WordToken::class.java), WordToken("word"))
            Assert.assertEquals(parser.readToken(NumberToken::class.java), NumberToken(22))
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken("string 1:x"))
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken(" "))
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken("Тест"))
            Assert.assertEquals(parser.readToken(ListBeginToken::class.java), ListBeginToken.instance)
            Assert.assertEquals(parser.readToken(WordToken::class.java), WordToken("sublist"))
            Assert.assertEquals(parser.readToken(ListEndToken::class.java), ListEndToken.instance)
            Assert.assertEquals(parser.readToken(ListEndToken::class.java), ListEndToken.instance)
            try {
                parser.readToken(ListEndToken::class.java)
                Assert.fail()
            } catch (ignored: EOFException) {
            }
        }
    }

    @Test
    fun test2dString() {
        ByteArrayInputStream("( ( 1:a ) ( 1:b ) )".toByteArray(StandardCharsets.UTF_8)).use { stream ->
            val parser = SvnServerParser(stream)
            val actual = parse(Array<Array<String>>::class.java, parser)
            ArrayAsserts.assertArrayEquals(arrayOf(arrayOf("a"), arrayOf("b")), actual)
        }
    }

    @Test
    fun testMessageParse() {
        ByteArrayInputStream("( 2 ( edit-pipeline svndiff1 absent-entries depth mergeinfo log-revprops ) 15:svn://localhost 31:SVN/1.8.8 (x86_64-pc-linux-gnu) ( ) ) test ".toByteArray(StandardCharsets.UTF_8)).use { stream ->
            val parser = SvnServerParser(stream)
            val req = parse(ClientInfo::class.java, parser)
            Assert.assertEquals(req.protocolVersion, 2)
            Assert.assertEquals(req.url.toString(), "svn://localhost")
            Assert.assertEquals(req.raClient, "SVN/1.8.8 (x86_64-pc-linux-gnu)")
            ArrayAsserts.assertArrayEquals(
                arrayOf(
                    "edit-pipeline",
                    "svndiff1",
                    "absent-entries",
                    "depth",
                    "mergeinfo",
                    "log-revprops"
                ), req.capabilities
            )
            Assert.assertEquals(parser.readText(), "test")
        }
    }

    @Test
    fun testMessageParse2() {
        ByteArrayInputStream("( 2 ( edit-pipeline svndiff1 absent-entries depth mergeinfo log-revprops ) 15:svn://localhost ) test ".toByteArray(StandardCharsets.UTF_8)).use { stream ->
            val parser = SvnServerParser(stream)
            val req = parse(ClientInfo::class.java, parser)
            Assert.assertEquals(req.protocolVersion, 2)
            Assert.assertEquals(req.url.toString(), "svn://localhost")
            Assert.assertEquals(req.raClient, "")
            ArrayAsserts.assertArrayEquals(
                arrayOf(
                    "edit-pipeline",
                    "svndiff1",
                    "absent-entries",
                    "depth",
                    "mergeinfo",
                    "log-revprops"
                ), req.capabilities
            )
            Assert.assertEquals(parser.readText(), "test")
        }
    }

    @Test
    fun testBinaryData() {
        val data = ByteArray(0x100)
        for (i in data.indices) {
            data[i] = i.toByte()
        }
        val streamData: ByteArray
        ByteArrayOutputStream().use { outputStream ->
            SvnServerWriter(outputStream).use { writer ->
                writer.write(StringToken(data))
                writer.write(StringToken(data))
                writer.write(WordToken("end"))
                streamData = outputStream.toByteArray()
            }
        }
        ByteArrayInputStream(streamData).use { inputStream ->
            val parser = SvnServerParser(inputStream)
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken(data))
            Assert.assertEquals(parser.readToken(StringToken::class.java), StringToken(data))
            Assert.assertEquals(parser.readToken(WordToken::class.java), WordToken("end"))
        }
    }
}
