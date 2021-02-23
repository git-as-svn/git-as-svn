/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser

import svnserver.parser.MessageParser.Parser
import svnserver.parser.token.*
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.Parameter
import java.util.*

/**
 * Parse data from class.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object MessageParser {

    private val emptyBytes: ByteArray = byteArrayOf()
    private val emptyInts: IntArray = intArrayOf()
    private val parsers: MutableMap<Class<*>?, Parser>

    @Throws(IOException::class)
    fun <T> parse(type: Class<T>, tokenParser: SvnServerParser?): T {
        val typeParser: Parser? = parsers[type]
        if (typeParser != null) {
            return typeParser.parse(tokenParser) as T
        }
        return parseObject(type, tokenParser)
    }

    @Throws(IOException::class)
    private fun <T> parseObject(type: Class<T>, tokenParser: SvnServerParser?): T {
        var tokenParser: SvnServerParser? = tokenParser
        if (tokenParser != null && tokenParser.readItem(ListBeginToken::class.java) == null) tokenParser = null
        val depth: Int = getDepth(tokenParser)
        if (type.isArray) {
            val result = ArrayList<Any>()
            if (tokenParser != null) {
                while (true) {
                    val element: Any = parse(type.componentType, tokenParser)
                    if (getDepth(tokenParser) < depth) break
                    result.add(element)
                }
            }
            return result.toArray(java.lang.reflect.Array.newInstance(type.componentType, result.size) as Array<*>) as T
        }
        val ctors: Array<Constructor<*>> = type.declaredConstructors
        if (ctors.size != 1) {
            throw IllegalStateException("Can't find parser ctor for object: " + type.name)
        }
        val ctor: Constructor<*> = ctors[0]
        val ctorParams: Array<Parameter> = ctor.parameters
        val params: Array<Any?> = arrayOfNulls(ctorParams.size)
        for (i in params.indices) {
            params[i] = parse(ctorParams[i].type, if (getDepth(tokenParser) == depth) tokenParser else null)
        }
        while (tokenParser != null && getDepth(tokenParser) >= depth) {
            tokenParser.readToken()
        }
        try {
            if (!ctor.isAccessible) ctor.isAccessible = true
            return ctor.newInstance(*params) as T
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException(e)
        }
    }

    @Throws(IOException::class)
    private fun parseString(tokenParser: SvnServerParser?): String {
        if (tokenParser == null) {
            return ""
        }
        val token: TextToken? = tokenParser.readItem(TextToken::class.java)
        return token?.text ?: ""
    }

    @Throws(IOException::class)
    private fun parseBinary(tokenParser: SvnServerParser?): ByteArray? {
        if (tokenParser == null) {
            return emptyBytes
        }
        val token: StringToken? = tokenParser.readItem(StringToken::class.java)
        return token?.data ?: emptyBytes
    }

    @Throws(IOException::class)
    private fun parseInt(tokenParser: SvnServerParser?): Int {
        if (tokenParser == null) {
            return 0
        }
        val token: NumberToken? = tokenParser.readItem(NumberToken::class.java)
        return if (token != null) token.number else 0
    }

    @Throws(IOException::class)
    private fun parseBool(tokenParser: SvnServerParser?): Boolean {
        if (tokenParser == null) {
            return false
        }
        val token: WordToken? = tokenParser.readItem(WordToken::class.java)
        return token != null && (token.text == "true")
    }

    @Throws(IOException::class)
    private fun parseInts(tokenParser: SvnServerParser?): IntArray {
        if (tokenParser == null) {
            return emptyInts
        }
        if (tokenParser.readItem((ListBeginToken::class.java)) != null) {
            val result = ArrayList<Int>()
            while (true) {
                val token: NumberToken = tokenParser.readItem(NumberToken::class.java) ?: break
                result.add(token.number)
            }
            val array = IntArray(result.size)
            for (i in array.indices) {
                array[i] = result[i]
            }
            return array
        }
        return emptyInts
    }

    private fun getDepth(tokenParser: SvnServerParser?): Int {
        return tokenParser?.depth ?: -1
    }

    private fun interface Parser {
        @Throws(IOException::class)
        fun parse(tokenParser: SvnServerParser?): Any?
    }

    init {
        parsers = HashMap()
        parsers[String::class.java] = Parser { obj: SvnServerParser? -> parseString(obj) }
        parsers[ByteArray::class.java] = Parser { obj: SvnServerParser? -> parseBinary(obj) }
        parsers[Int::class.javaPrimitiveType] = Parser { obj: SvnServerParser? -> parseInt(obj) }
        parsers[IntArray::class.java] = Parser { obj: SvnServerParser? -> parseInts(obj) }
        parsers[Boolean::class.javaPrimitiveType] = Parser { obj: SvnServerParser? -> parseBool(obj) }
    }
}
