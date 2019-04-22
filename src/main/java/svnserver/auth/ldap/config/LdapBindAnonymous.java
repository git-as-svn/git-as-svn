/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.ANONYMOUSBindRequest;
import com.unboundid.ldap.sdk.BindRequest;
import org.jetbrains.annotations.NotNull;
import svnserver.config.serializer.ConfigType;

@ConfigType("ldapBindAnonymous")
public final class LdapBindAnonymous implements LdapBind {

  @NotNull
  public static final LdapBind instance = new LdapBindAnonymous();

  @Override
  public @NotNull BindRequest createBindRequest() {
    return new ANONYMOUSBindRequest();
  }
}
