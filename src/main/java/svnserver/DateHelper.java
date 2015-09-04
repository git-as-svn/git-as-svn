/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Helpers for datet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class DateHelper {
  @NotNull
  public static String toISO8601(@NotNull Instant time) {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
        .withZone(ZoneOffset.UTC)
        .format(time);
  }

  @NotNull
  public static String toISO8601(long time) {
    return toISO8601(Instant.ofEpochMilli(time));
  }
}
