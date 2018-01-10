/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.CRAMMD5BindRequest;
import com.unboundid.ldap.sdk.DIGESTMD5BindRequest;
import com.unboundid.ldap.sdk.PLAINBindRequest;
import org.jetbrains.annotations.NotNull;
import svnserver.config.serializer.ConfigType;

/**
 * LDAP bind by Dn with password.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("ldapBindSimple")
public final class LdapBindSimple implements LdapBind {
  @NotNull
  private BindType bindType;
  @NotNull
  private String username;
  @NotNull
  private String password;

  public LdapBindSimple() {
    this(BindType.DigestMD5, "", "");
  }

  public LdapBindSimple(@NotNull BindType bindType, @NotNull String username, @NotNull String password) {
    this.bindType = bindType;
    this.username = username;
    this.password = password;
  }

  @Override
  @NotNull
  public BindRequest createBindRequest() {
    return bindType.createBindRequest(username, password);
  }

  public enum BindType {
    Plain {
      @Override
      public @NotNull BindRequest createBindRequest(@NotNull String username, @NotNull String password) {
        return new PLAINBindRequest(username, password);
      }
    },

    DigestMD5 {
      @Override
      public @NotNull BindRequest createBindRequest(@NotNull String username, @NotNull String password) {
        return new DIGESTMD5BindRequest(username, password);
      }
    },

    CramMD5 {
      @Override
      public @NotNull BindRequest createBindRequest(@NotNull String username, @NotNull String password) {
        return new CRAMMD5BindRequest(username, password);
      }
    };

    @NotNull
    public abstract BindRequest createBindRequest(@NotNull String username, @NotNull String password);
  }
}
