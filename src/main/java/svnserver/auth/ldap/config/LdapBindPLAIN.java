/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.PLAINBindRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.config.serializer.ConfigType;

/**
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see PLAINBindRequest
 */
@ConfigType("PLAIN")
public final class LdapBindPLAIN implements LdapBind {
  @NotNull
  private String authenticationID;
  @Nullable
  private String authorizationID;
  @NotNull
  private String password;

  public LdapBindPLAIN() {
    this("", "");
  }

  public LdapBindPLAIN(@NotNull String authenticationID, @NotNull String password) {
    this(authenticationID, null, password);
  }

  private LdapBindPLAIN(@NotNull String authenticationID, @Nullable String authorizationID, @NotNull String password) {
    this.authenticationID = authenticationID;
    this.authorizationID = authorizationID;
    this.password = password;
  }

  @Override
  @NotNull
  public BindRequest createBindRequest() {
    return new PLAINBindRequest(authenticationID, authorizationID, password);
  }
}
