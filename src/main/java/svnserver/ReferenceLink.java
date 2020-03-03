/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;

/**
 * Helper for create documentation links.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public enum ReferenceLink {
  InvalidSvnProps("invalid-svn-props");

  @NotNull
  public static final String BASE_URL = "https://bozaro.github.io/git-as-svn/htmlsingle/git-as-svn.html";

  @NotNull
  private final String anchor;

  ReferenceLink(@NotNull String anchor) {
    this.anchor = anchor;
  }

  @NotNull
  public final String getLink() {
    return BASE_URL + "#" + anchor;
  }
}
