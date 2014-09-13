/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.msg;

import org.jetbrains.annotations.NotNull;

/**
 * Message from client with authentication type.
 * <p>
 * auth-response: ( mech:word [ token:string ] )
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class AuthReq {
  @NotNull
  private final String mech;
  @NotNull
  private final String[] token;

  public AuthReq(@NotNull String mech, @NotNull String[] token) {
    this.mech = mech;
    this.token = token;
  }

  @NotNull
  public String getMech() {
    return mech;
  }

  @NotNull
  public String getToken() {
    return token.length < 1 ? "" : token[0];
  }
}
