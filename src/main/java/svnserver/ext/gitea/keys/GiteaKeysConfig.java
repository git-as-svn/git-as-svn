/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.keys;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.gitea.config.GiteaContext;
import svnserver.ext.keys.KeysConfig;
import svnserver.ext.keys.SSHDirectoryWatcher;

/**
 * SSH Keys watcher configuration
 * 
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
@ConfigType("giteaSSHKeys")
public class GiteaKeysConfig extends KeysConfig {

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    GiteaContext giteaContext = context.sure(GiteaContext.class);
    GiteaKeysMapper mapper = new GiteaKeysMapper(giteaContext);
    context.add(GiteaKeysMapper.class, mapper);
    SSHDirectoryWatcher watcher = new SSHDirectoryWatcher(this, mapper);
    context.add(SSHDirectoryWatcher.class, watcher);
  }

}