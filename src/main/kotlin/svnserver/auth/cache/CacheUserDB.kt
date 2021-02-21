/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache

import com.google.common.cache.Cache
import org.apache.commons.codec.binary.Hex
import org.tmatesoft.svn.core.SVNException
import svnserver.HashHelper
import svnserver.UserType
import svnserver.auth.Authenticator
import svnserver.auth.PlainAuthenticator
import svnserver.auth.User
import svnserver.auth.UserDB
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ExecutionException

/**
 * Caching user authentication result for reduce external API usage.
 *
 * @author Artem V. Navrotskiy
 */
class CacheUserDB(private val userDB: UserDB, private val cache: Cache<String?, User>) : UserDB {
    private val authenticators: Collection<Authenticator> = setOf(PlainAuthenticator(this))
    override fun authenticators(): Collection<Authenticator> {
        return authenticators
    }

    @Throws(SVNException::class)
    override fun check(username: String, password: String): User? {
        return cached("c." + hash(username, password)) { db: UserDB -> db.check(username, password) }
    }

    @Throws(SVNException::class)
    override fun lookupByUserName(username: String): User? {
        return cached("l.$username") { db: UserDB -> db.lookupByUserName(username) }
    }

    @Throws(SVNException::class)
    override fun lookupByExternal(external: String): User? {
        return cached("e.$external") { db: UserDB -> db.lookupByExternal(external) }
    }

    @Throws(SVNException::class)
    private fun cached(key: String, callback: CachedCallback): User? {
        return try {
            val cachedUser = cache[key, {
                val authUser = callback.exec(userDB)
                authUser ?: invalidUser
            }]
            if (cachedUser !== invalidUser) cachedUser else null
        } catch (e: ExecutionException) {
            if (e.cause is SVNException) {
                throw (e.cause as SVNException?)!!
            }
            throw IllegalStateException(e)
        }
    }

    private fun hash(username: String, password: String): String {
        val digest = HashHelper.sha256()
        hashPacket(digest, username.toByteArray(StandardCharsets.UTF_8))
        hashPacket(digest, password.toByteArray(StandardCharsets.UTF_8))
        return Hex.encodeHexString(digest.digest())
    }

    private fun hashPacket(digest: MessageDigest, packet: ByteArray) {
        var length = packet.size
        while (true) {
            digest.update((length and 0xFF).toByte())
            if (length == 0) {
                break
            }
            length = length shr 8 and 0xFFFFFF
        }
        digest.update(packet)
    }

    private fun interface CachedCallback {
        @Throws(SVNException::class)
        fun exec(userDB: UserDB): User?
    }

    companion object {
        private val invalidUser: User = User.create("invalid", "invalid", null, null, UserType.Local, null)
    }
}
