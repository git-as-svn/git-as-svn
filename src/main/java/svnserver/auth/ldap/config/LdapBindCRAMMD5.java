/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.CRAMMD5BindRequest;
import org.jetbrains.annotations.NotNull;
import svnserver.config.serializer.ConfigType;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see CRAMMD5BindRequest
 */
@ConfigType("CRAMMD5")
public final class LdapBindCRAMMD5 implements LdapBind {
  @NotNull
  private String authenticationID;
  @NotNull
  private String password;

  public LdapBindCRAMMD5() {
    this("", "");
  }

  private LdapBindCRAMMD5(@NotNull String authenticationID, @NotNull String password) {
    this.authenticationID = authenticationID;
    this.password = password;
  }

  @Override
  @NotNull
  public BindRequest createBindRequest() {
    return new CRAMMD5BindRequest(authenticationID, password);
  }
}
