/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.LocalConfig;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.api.ApiProvider;

import java.io.IOException;

/**
 * API configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("api")
public class ApiConfig implements SharedConfig, LocalConfig {
  @NotNull
  private String path = "/api";

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(ApiProvider.class, new ApiProvider(path));
  }

  @Override
  public void create(@NotNull LocalContext context) throws IOException {
    ApiProvider provider = new ApiProvider(context.getName() + path);
    context.add(ApiProvider.class, provider);
    provider.init(context);
  }
}
