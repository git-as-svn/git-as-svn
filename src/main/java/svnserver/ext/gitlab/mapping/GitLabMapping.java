/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import com.google.common.hash.Hashing;
import org.gitlab.api.models.GitlabProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.StringHelper;
import svnserver.config.ConfigHelper;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.VcsAccess;
import svnserver.repository.git.GitRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitLabMapping implements RepositoryMapping<GitLabProject> {

  @NotNull
  private static final String HASHED_PATH = "@hashed";

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

  @NotNull
  public NavigableMap<String, GitLabProject> getMapping() {
    return mapping;
  }

  @Nullable
  GitLabProject updateRepository(@NotNull GitlabProject project) throws IOException {
    if (!tagsMatch(project)) {
      removeRepository(project.getId(), project.getPathWithNamespace());
      return null;
    }

    final String projectName = project.getPathWithNamespace();
    final String projectKey = StringHelper.normalizeDir(projectName);
    final GitLabProject oldProject = mapping.get(projectKey);
    if (oldProject == null || oldProject.getProjectId() != project.getId()) {
      final File basePath = ConfigHelper.joinPath(context.getBasePath(), config.getPath());
      final String sha256 = Hashing.sha256().hashString(project.getId().toString(), Charset.defaultCharset()).toString();
      File repoPath = Paths.get(basePath.toString(), HASHED_PATH, sha256.substring(0, 2), sha256.substring(2, 4), sha256 + ".git").toFile();
      if (!repoPath.exists())
        repoPath = ConfigHelper.joinPath(basePath, project.getPathWithNamespace() + ".git");
      final LocalContext local = new LocalContext(context, project.getPathWithNamespace());
      local.add(VcsAccess.class, new GitLabAccess(local, config, project.getId()));
      final GitRepository repository = config.getTemplate().create(local, repoPath);
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
