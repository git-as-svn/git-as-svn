/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import org.gitlab.api.models.GitlabProject;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.ConfigHelper;
import svnserver.config.GitRepositoryConfig;
import svnserver.config.RepositoryMappingConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.repository.VcsRepositoryMapping;
import svnserver.repository.mapping.RepositoryListMapping;

import java.io.File;
import java.io.IOException;

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("gitlabMapping")
public class GitLabMappingConfig implements RepositoryMappingConfig {
  @NotNull
  private GitRepositoryConfig template = new GitRepositoryConfig();
  @NotNull
  private String path = "/var/git/repositories/";

  @NotNull
  @Override
  public VcsRepositoryMapping create(@NotNull SharedContext context) throws IOException, SVNException {
    GitLabContext gitlab = context.sure(GitLabContext.class);
    final RepositoryListMapping.Builder builder = new RepositoryListMapping.Builder();
    final File basePath = ConfigHelper.joinPath(context.getBasePath(), path);
    for (GitlabProject project : gitlab.connect().getAllProjects()) {
      final File repoPath = ConfigHelper.joinPath(basePath, project.getPathWithNamespace() + ".git");
      final LocalContext local = new LocalContext(context, project.getPathWithNamespace());
      builder.add(project.getPathWithNamespace(), template.create(local, repoPath));
    }
    return builder.build();
  }
}
