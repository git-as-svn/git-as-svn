/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import org.gitlab.api.TokenType;
import org.jetbrains.annotations.NotNull;

public final class GitLabToken {

  @NotNull
  private final TokenType type;

  @NotNull
  private final String value;

  public GitLabToken(@NotNull TokenType type, @NotNull String value) {
    this.type = type;
    this.value = value;
  }

  @NotNull
  public TokenType getType() {
    return type;
  }

  @NotNull
  public String getValue() {
    return value;
  }
}
