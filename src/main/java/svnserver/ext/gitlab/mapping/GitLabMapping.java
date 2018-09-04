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
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.StringHelper;
import svnserver.config.ConfigHelper;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.repository.RepositoryInfo;
import svnserver.repository.VcsAccess;
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRepositoryMapping;
import svnserver.repository.mapping.RepositoryListMapping;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitLabMapping implements VcsRepositoryMapping {
  @NotNull
  private final NavigableMap<String, GitLabProject> mapping = new ConcurrentSkipListMap<>();
  @NotNull
  private final SharedContext context;
  @NotNull
  private final GitLabMappingConfig config;

  GitLabMapping(@NotNull SharedContext context, @NotNull GitLabMappingConfig config) {
    this.context = context;
    this.config = config;
  }

  @NotNull
  public SharedContext getContext() {
    return context;
  }

  @Nullable
  @Override
  public RepositoryInfo getRepository(@NotNull SVNURL url) throws SVNException {
    final Map.Entry<String, GitLabProject> entry = RepositoryListMapping.getMapped(mapping, url.getPath());
    if (entry != null && entry.getValue().isReady()) {
      return new RepositoryInfo(
          SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort() == SVNURL.getDefaultPortNumber(url.getProtocol()) ? -1 : url.getPort(), entry.getKey(), true),
          entry.getValue().getRepository()
      );
    }
    return null;
  }

  @NotNull
  Collection<GitLabProject> getRepositories() {
    return mapping.values();
  }

  @Nullable
  GitLabProject updateRepository(@NotNull GitlabProject project) throws IOException, SVNException {
    if (!tagsMatch(project)) {
      removeRepository(project.getId(), project.getPathWithNamespace());
      return null;
    }

    final String projectName = project.getPathWithNamespace();
    final String projectKey = StringHelper.normalizeDir(projectName);
    final GitLabProject oldProject = mapping.get(projectKey);
    if (oldProject == null || oldProject.getProjectId() != project.getId()) {
      final File basePath = ConfigHelper.joinPath(context.getBasePath(), config.getPath());
      final File repoPath = ConfigHelper.joinPath(basePath, project.getPathWithNamespace() + ".git");
      final LocalContext local = new LocalContext(context, project.getPathWithNamespace());
      local.add(VcsAccess.class, new GitLabAccess(local, config, project.getId()));
      final VcsRepository repository = config.getTemplate().create(local, repoPath);
      final GitLabProject newProject = new GitLabProject(local, repository, project.getId());
      if (mapping.compute(projectKey, (key, value) -> value != null && value.getProjectId() == project.getId() ? value : newProject) == newProject) {
        return newProject;
      }
    }
    return null;
  }

  private boolean tagsMatch(@NotNull GitlabProject project) {
    if (config.getRepositoryTags().isEmpty()) {
      return true;
    }

    for (String tag : project.getTagList()) {
      if (config.getRepositoryTags().contains(tag)) {
        return true;
      }
    }
    return false;
  }

  void removeRepository(int projectId, @NotNull String projectName) {
    final String projectKey = StringHelper.normalizeDir(projectName);
    final GitLabProject project = mapping.get(projectKey);
    if (project != null && project.getProjectId() == projectId) {
      if (mapping.remove(projectKey, project)) {
        project.close();
      }
    }
  }
}
