/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping;

import io.gitea.model.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.StringHelper;
import svnserver.config.ConfigHelper;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.repository.RepositoryInfo;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.VcsAccess;
import svnserver.repository.git.GitRepository;
import svnserver.repository.mapping.RepositoryListMapping;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
final class GiteaMapping implements RepositoryMapping {
  @NotNull
  private final NavigableMap<String, GiteaProject> mapping = new ConcurrentSkipListMap<>();
  @NotNull
  private final SharedContext context;
  @NotNull
  private final GiteaMappingConfig config;

  GiteaMapping(@NotNull SharedContext context, @NotNull GiteaMappingConfig config) {
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
    final Map.Entry<String, GiteaProject> entry = RepositoryListMapping.getMapped(mapping, url.getPath());
    if (entry != null && entry.getValue().isReady()) {
      return new RepositoryInfo(
          SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort() == SVNURL.getDefaultPortNumber(url.getProtocol()) ? -1 : url.getPort(), entry.getKey(), true),
          entry.getValue().getRepository()
      );
    }
    return null;
  }

  @NotNull
  Collection<GiteaProject> getRepositories() {
    return mapping.values();
  }

  @Nullable GiteaProject addRepository(@NotNull Repository repository) throws IOException, SVNException {
    final String projectName = repository.getFullName();
    final String projectKey = StringHelper.normalizeDir(projectName);
    final GiteaProject oldProject = mapping.get(projectKey);
    if (oldProject == null || oldProject.getProjectId() != repository.getId()) {
      final File basePath = ConfigHelper.joinPath(context.getBasePath(), config.getPath());
      // the repository name is lowercased as per gitea cmd/serv.go:141
      final File repoPath = ConfigHelper.joinPath(basePath, repository.getFullName().toLowerCase(Locale.ENGLISH) + ".git");
      final LocalContext local = new LocalContext(context, repository.getFullName());
      local.add(VcsAccess.class, new GiteaAccess(local, config, repository));
      final GitRepository vcsRepository = config.getTemplate().create(local, repoPath);
      final GiteaProject newProject = new GiteaProject(local, vcsRepository, repository.getId(), repository.getOwner().getLogin(), projectName);
      if (mapping.compute(projectKey, (key, value) -> value != null && value.getProjectId() == repository.getId() ? value : newProject) == newProject) {
        return newProject;
      }
    }
    return null;
  }

  void removeRepository(@NotNull String projectName) {
    final String projectKey = StringHelper.normalizeDir(projectName);
    final GiteaProject project = mapping.get(projectKey);
    if (project != null) {
      if (mapping.remove(projectKey, project)) {
        project.close();
      }
    }
  }
}
