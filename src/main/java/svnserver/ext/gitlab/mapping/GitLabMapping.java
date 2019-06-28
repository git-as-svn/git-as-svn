/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import com.google.common.hash.Hashing;
import org.eclipse.jgit.util.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.GitlabAPIException;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabSystemHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNException;
import svnserver.Loggers;
import svnserver.StringHelper;
import svnserver.config.ConfigHelper;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.ext.web.server.WebServer;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.VcsAccess;
import svnserver.repository.git.GitRepository;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitLabMapping implements RepositoryMapping<GitLabProject> {
  @NotNull
  private static final Logger log = Loggers.gitlab;

  @NotNull
  private static final String HASHED_PATH = "@hashed";

  @NotNull
  private final NavigableMap<String, GitLabProject> mapping = new ConcurrentSkipListMap<>();
  @NotNull
  private final SharedContext context;
  @NotNull
  private final GitLabMappingConfig config;
  private final GitLabContext gitLabContext;

  GitLabMapping(@NotNull SharedContext context, @NotNull GitLabMappingConfig config, @NotNull GitLabContext gitLabContext) {
    this.context = context;
    this.config = config;
    this.gitLabContext = gitLabContext;
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

  @Override
  public void ready(@NotNull SharedContext context) throws IOException {
    final GitlabAPI api = gitLabContext.connect();

    // Web hook for repository list update.
    final WebServer webServer = context.sure(WebServer.class);
    final URL hookUrl = webServer.toUrl(gitLabContext.getHookPath());
    final String path = hookUrl.getPath();
    webServer.addServlet(StringUtils.isEmptyOrNull(path) ? "/" : path, new GitLabHookServlet());

    try {
      if (!isHookInstalled(api, hookUrl.toString())) {
        api.addSystemHook(hookUrl.toString());
      }
    } catch (GitlabAPIException e) {
      if (e.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
        log.warn("Unable to install gitlab hook {}: {}", hookUrl, e.getMessage());
      } else {
        throw e;
      }
    }

  }

  private boolean isHookInstalled(@NotNull GitlabAPI api, @NotNull String hookUrl) throws IOException {
    final List<GitlabSystemHook> hooks = api.getSystemHooks();
    for (GitlabSystemHook hook : hooks) {
      if (hook.getUrl().equals(hookUrl)) {
        return true;
      }
    }
    return false;
  }

  private class GitLabHookServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      log.info("GitLab system hook fire ...");
      final GitLabHookEvent event = parseEvent(req);
      final String msg = "Can't parse event data";
      if (event == null || event.getEventName() == null) {
        log.warn(msg);
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        return;
      }
      try {
        log.debug(event.getEventName() + " event happened, process ...");
        switch (event.getEventName()) {
          case "project_create":
          case "project_update":
          case "project_rename":
          case "project_transfer":
            if (event.getProjectId() == null || event.getPathWithNamespace() == null) {
              log.warn(msg);
              resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
              return;
            }
            final GitlabAPI api = gitLabContext.connect();
            final GitLabProject project = updateRepository(api.getProject(event.getProjectId()));
            if (project != null) {
              log.info(event.getEventName() + " event happened, init project revisions ...");
              project.initRevisions();
            } else {
              log.warn(event.getEventName() + " event happened, but can not found project!");
            }
            return;
          case "project_destroy":
            if (event.getProjectId() == null || event.getPathWithNamespace() == null) {
              resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Can't parse event data");
              return;
            }
            removeRepository(event.getProjectId(), event.getPathWithNamespace());
            break;
          default:
            // Ignore hook.
            log.info(event.getEventName() + " event not process, ignore this hook event.");
            return;
        }
        super.doPost(req, resp);
      } catch (FileNotFoundException inored) {
        log.warn("Event repository not exists: " + event.getProjectId());
      } catch (SVNException e) {
        log.error("Event processing error: " + event.getEventName(), e);
        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      }
    }

    @Nullable
    private GitLabHookEvent parseEvent(@NotNull HttpServletRequest req) {
      try (final Reader reader = req.getReader()) {
        return GitLabHookEvent.parseEvent(reader);
      } catch (IOException e) {
        log.warn("Can't read hook data", e);
        return null;
      }
    }
  }
}
