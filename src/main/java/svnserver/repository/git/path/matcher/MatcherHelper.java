/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher;

import org.jetbrains.annotations.NotNull;

/**
 * Matcher helper.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class MatcherHelper {
  private MatcherHelper() {
  }

  public static String stripSlash(@NotNull String token) {
    return token.endsWith("/") ? token.substring(0, token.length() - 1) : token;
  }
}
