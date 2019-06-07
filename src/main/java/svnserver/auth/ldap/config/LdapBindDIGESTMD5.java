/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.DIGESTMD5BindRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.config.serializer.ConfigType;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see DIGESTMD5BindRequest
 */
@ConfigType("DIGESTMD5")
public final class LdapBindDIGESTMD5 implements LdapBind {
  @NotNull
  private String authenticationID;
  @Nullable
  private String authorizationID;
  @Nullable
  private String realm;
  @NotNull
  private String password;

  public LdapBindDIGESTMD5() {
    this("", null, "", null);
  }

  private LdapBindDIGESTMD5(@NotNull String authenticationID, @Nullable String authorizationID, @NotNull String password, @Nullable String realm) {
    this.authenticationID = authenticationID;
    this.authorizationID = authorizationID;
    this.realm = realm;
    this.password = password;
  }

  @Override
  @NotNull
  public BindRequest createBindRequest() {
    return new DIGESTMD5BindRequest(authenticationID, authorizationID, password, realm);
  }
}
