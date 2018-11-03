/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.keys;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

import svnserver.auth.Authenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;

public final class KeyAuthenticator implements Authenticator {
    @NotNull
    private final UserDB userDB;
    @NotNull
    private final String secretToken;

    public KeyAuthenticator(@NotNull UserDB userDB,
                            @NotNull String secretToken) {
        this.userDB = userDB;
        this.secretToken = secretToken;
    }

    @NotNull
    @Override
    public String getMethodName() {
        return "KEY-AUTHENTICATOR";
    }

    @Nullable
    @Override
    public User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer, @NotNull String token) throws SVNException {
        final String decodedToken = new String(Base64.getDecoder().decode(token.trim()), StandardCharsets.US_ASCII);
        final String[] credentials = decodedToken.split("\u0000");

        if (credentials.length < 3)
            return null;

        final String clientSecretToken = credentials[1];

        if (clientSecretToken.trim().equals(this.secretToken)) {
            final String username = credentials[2];
            return userDB.lookupByExternal(username);
        }
        return null;
    }
}