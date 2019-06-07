/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.EXTERNALBindRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.config.serializer.ConfigType;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see EXTERNALBindRequest
 */
@ConfigType("EXTERNAL")
public final class LdapBindEXTERNAL implements LdapBind {
  @Nullable
  private String authzID;

  public LdapBindEXTERNAL() {
    this(null);
  }

  private LdapBindEXTERNAL(@Nullable String authzID) {
    this.authzID = authzID;
  }

  @Override
  @NotNull
  public BindRequest createBindRequest() {
    return new EXTERNALBindRequest(authzID);
  }
}
