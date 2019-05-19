/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import org.eclipse.jgit.util.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.GitlabAPIException;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabSystemHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.GitRepositoryConfig;
import svnserver.config.RepositoryMappingConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.ext.web.server.WebServer;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.git.GitCreateMode;

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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("gitlabMapping")
public final class GitLabMappingConfig implements RepositoryMappingConfig {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitLabHookServlet.class);
  @NotNull
  private GitRepositoryConfig template;
  @NotNull
  private String path;
  private int cacheTimeSec = 15;
  private int cacheMaximumSize = 1000;
  @NotNull
  private Set<String> repositoryTags;

  public GitLabMappingConfig() {
    this("/var/git/repositories/", GitCreateMode.ERROR, Collections.emptySet());
  }

  private GitLabMappingConfig(@NotNull String path, @NotNull GitCreateMode createMode, @NotNull Set<String> repositoryTags) {
    this.path = path;
    this.template = new GitRepositoryConfig(createMode);
    this.repositoryTags = repositoryTags;
  }

  public GitLabMappingConfig(@NotNull File path, @NotNull GitCreateMode createMode) {
    this(path.getAbsolutePath(), createMode, Collections.emptySet());
  }

  public GitLabMappingConfig(@NotNull File path, @NotNull GitCreateMode createMode, @NotNull Set<String> repositoryTags) {
    this(path.getAbsolutePath(), createMode, repositoryTags);
  }

  @NotNull
  Set<String> getRepositoryTags() {
    return repositoryTags;
  }

  @NotNull
  GitRepositoryConfig getTemplate() {
    return template;
  }

  @NotNull
  String getPath() {
    return path;
  }

  int getCacheTimeSec() {
    return cacheTimeSec;
  }

  int getCacheMaximumSize() {
    return cacheMaximumSize;
  }

  @NotNull
  @Override
  public RepositoryMapping create(@NotNull SharedContext context, boolean canUseParallelIndexing) throws IOException {
    final GitLabContext gitlab = context.sure(GitLabContext.class);
    final GitlabAPI api = gitlab.connect();
    // Get repositories.

    final GitLabMapping mapping = new GitLabMapping(context, this);
    for (GitlabProject project : api.getProjects()) {
      mapping.updateRepository(project);
    }
    // Web hook for repository list update.
    final WebServer webServer = WebServer.get(context);
    final URL hookUrl = new URL(gitlab.getHookUrl());
    final String path = hookUrl.getPath();
    webServer.addServlet(StringUtils.isEmptyOrNull(path) ? "/" : path, new GitLabHookServlet(mapping));

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

    final Consumer<GitLabProject> init = repository -> {
      try {
        repository.initRevisions();
      } catch (IOException | SVNException e) {
        throw new RuntimeException(String.format("[%s]: failed to initialize", repository), e);
      }
    };

    if (canUseParallelIndexing) {
      mapping.getMapping().values().parallelStream().forEach(init);
    } else {
      mapping.getMapping().values().forEach(init);
    }

    return mapping;
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

  private static class GitLabHookServlet extends HttpServlet {
    @NotNull
    private final GitLabMapping mapping;

    GitLabHookServlet(@NotNull GitLabMapping mapping) {
      this.mapping = mapping;
    }

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
            final GitlabAPI api = mapping.getContext().sure(GitLabContext.class).connect();
            final GitLabProject project = mapping.updateRepository(api.getProject(event.getProjectId()));
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
            mapping.removeRepository(event.getProjectId(), event.getPathWithNamespace());
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
