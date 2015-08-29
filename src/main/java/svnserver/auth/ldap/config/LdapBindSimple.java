/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.DIGESTMD5BindRequest;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import org.jetbrains.annotations.NotNull;
import svnserver.config.serializer.ConfigType;

/**
 * LDAP bind by Dn with password.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("ldapBindSimple")
public class LdapBindSimple implements LdapBind {
  @NotNull
  private String username;
  @NotNull
  private String password;

  public LdapBindSimple() {
    this("", "");
  }

  public LdapBindSimple(@NotNull String username, @NotNull String password) {
    this.username = username;
    this.password = password;
  }

  @NotNull
  @Override
  public BindResult bind(@NotNull LDAPConnection connection) throws LDAPException {
    return connection.bind(new DIGESTMD5BindRequest(username, password));
  }
}
