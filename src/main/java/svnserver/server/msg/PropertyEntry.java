/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.msg;

import org.jetbrains.annotations.NotNull;

public final class PropertyEntry {
  @NotNull
  private final String name;
  @NotNull
  private final String value;

  public PropertyEntry(@NotNull String name, @NotNull String value) {
    this.name = name;
    this.value = value;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public String getValue() {
    return value;
  }
}
