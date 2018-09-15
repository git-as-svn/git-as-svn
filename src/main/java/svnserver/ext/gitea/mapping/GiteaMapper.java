/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;

import io.gitea.ApiClient;
import io.gitea.ApiException;
import io.gitea.api.RepositoryApi;
import io.gitea.model.Repository;


public class GiteaMapper extends Thread implements DirectoryWatcher.DirectoryMapping {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GiteaMapper.class);

  private LinkedList<String> toAdd = new LinkedList<>();
  private LinkedList<String> toRemove = new LinkedList<>();
  private GiteaMapping mapping;
  private RepositoryApi repositoryApi;

  public GiteaMapper(ApiClient apiClient, GiteaMapping mapping) {
    this.repositoryApi = new RepositoryApi(apiClient);
    this.mapping = mapping;
    this.start();
  }

  public void run() {
    try {
      while (true) {
        synchronized(toAdd) {
          for (Iterator<String> it = toRemove.iterator(); it.hasNext(); ) {
            String projectName = it.next();
            mapping.removeRepository(projectName);
            it.remove();
          }
          for (Iterator<String> it = toAdd.iterator(); it.hasNext();) {
            String projectName = it.next();
            String owner = projectName.substring(0, projectName.indexOf('/'));
            String repo = projectName.substring(projectName.indexOf('/') + 1);
            try {
              Repository repository = repositoryApi.repoGet(owner, repo);
              GiteaProject project = mapping.addRepository(repository);
              if (project != null) {
                project.initRevisions();
                it.remove();
              }
            } catch (ApiException e) {
              // Not ready yet - try again later...
            } catch (IOException | SVNException e) {
              log.error("Processing error whilst adding repository: {} / {}: {}", owner, repo, e.getMessage());
            }
          }
        }
        sleep(1000);
      }
    } catch(InterruptedException e) {
      return;
    }
  }

  @Override
  public void addRepository(String owner, String repo) {
    synchronized(toAdd) {
      toAdd.add(owner + "/" + repo);
    }
  }

  @Override
  public void removeRepositories(String owner) {
    synchronized(toAdd) {
      for (GiteaProject project : mapping.getRepositories()) {
        if (project.getOwner().equals(owner)) {
          toRemove.add(project.getRepositoryName());
          toAdd.remove(project.getRepositoryName());
        }
      }
    }
  }

  @Override
  public void removeRepository(String owner, String repo) {
    synchronized(toAdd) {
      toRemove.add(owner + "/" + repo);
      toAdd.remove(owner + "/" + repo);
    }
  }
}