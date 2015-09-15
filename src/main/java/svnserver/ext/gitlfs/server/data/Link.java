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
import java.util.Map;
import java.util.TreeMap;

/**
 * LFS reference.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class Link {
  @JsonProperty(value = "href", required = true)
  @NotNull
  private URI href;
  @JsonProperty("header")
  @Nullable
  private Map<String, String> header = new TreeMap<>();

  protected Link() {
  }

  public Link(@NotNull URI href, @Nullable Map<String, String> header) {
    this.href = href;
    this.header = header;
  }

  @NotNull
  public URI getHref() {
    return href;
  }

  @Nullable
  public Map<String, String> getHeader() {
    return header;
  }
}
