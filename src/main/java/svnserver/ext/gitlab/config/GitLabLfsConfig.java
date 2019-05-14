/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.BasicAuthHttpLfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;

/**
 * Git LFS configuration file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("gitlabLfs")
public final class GitLabLfsConfig implements SharedConfig, LfsStorageFactory {

  @Override
  public void create(@NotNull SharedContext context) {
    context.add(LfsStorageFactory.class, this);
  }

  @NotNull
  public LfsStorage createStorage(@NotNull LocalContext context) {
    final GitLabContext gitLabContext = GitLabContext.sure(context.getShared());
    final String repositoryUrl = gitLabContext.getGitLabUrl() + context.getName();

    // Okay, this could be *a bit* insecure. We're downloading LFS object on behalf of our git-as-svn user.
    // But we still perform all security checks, user needs to have access to path where this object is references.
    // This is done in order to keep objective view on repository state for git-as-svn (unknown things can break otherwise).
    final GitLabToken token = gitLabContext.getToken();

    return new BasicAuthHttpLfsStorage(repositoryUrl, "UNUSED", token.getValue());
  }
}
