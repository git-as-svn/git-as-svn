/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.token

import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers
import org.jose4j.jwe.JsonWebEncryption
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers
import org.jose4j.keys.AesKey
import java.security.Key

/**
 * WJT token with AES encryption.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class EncryptionFactoryAes(secret: String) : EncryptionFactory {
    private val key: Key

    init {
        key = AesKey(TokenHelper.secretToBytes(secret, KEY_SIZE))
    }

    override fun create(): JsonWebEncryption {
        val jwe = JsonWebEncryption()
        jwe.algorithmHeaderValue = KeyManagementAlgorithmIdentifiers.A128KW
        jwe.encryptionMethodHeaderParameter = ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256
        jwe.key = key
        return jwe
    }

    companion object {
        var KEY_SIZE = 0x10
    }
}
