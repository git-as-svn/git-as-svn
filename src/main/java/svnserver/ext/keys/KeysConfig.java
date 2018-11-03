/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.keys;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

@ConfigType("sshKeys")
public class KeysConfig implements SharedConfig {

	@Override
	public void create(@NotNull SharedContext context) throws IOException {
        SSHDirectoryWatcher watcher = new SSHDirectoryWatcher(this, null);
        context.add(SSHDirectoryWatcher.class, watcher);
	}

    @NotNull
    private String originalAppPath;

    @NotNull
    private String svnservePath;

    @NotNull
    private String shadowSSHDirectory;

    @NotNull
    private String realSSHDirectory;

    @NotNull
    public String getOriginalAppPath() {
        return originalAppPath;
    }

    @NotNull
    public String getSvnservePath() {
        return svnservePath;
    }
    
    @NotNull
    public String getShadowSSHDirectory() {
        return shadowSSHDirectory;
    }

    @NotNull
    public String getRealSSHDirectory() {
        return realSSHDirectory;
    }
}