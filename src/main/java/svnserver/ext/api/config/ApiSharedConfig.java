/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.api.ApiShared;

import java.io.IOException;

/**
 * API configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("api")
public class ApiSharedConfig implements SharedConfig {
  @NotNull
  private String path = "/api";

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(ApiShared.class, new ApiShared(path));
  }
}
