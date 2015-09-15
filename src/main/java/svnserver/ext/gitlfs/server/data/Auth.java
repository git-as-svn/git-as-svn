/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Date;
import java.util.Map;

/**
 * Auth structure.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class Auth extends Link {
  @JsonProperty("expires_at")
  @Nullable
  private Date expiresAt;

  protected Auth() {
  }

  public Auth(@NotNull URI href, @Nullable Map<String, String> header, @Nullable Date expiresAt) {
    super(href, header);
    this.expiresAt = expiresAt;
  }

  @Nullable
  public Date getExpiresAt() {
    return expiresAt;
  }
}
