/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.StringHelper;
import svnserver.context.Shared;
import svnserver.repository.git.BranchProvider;
import svnserver.repository.git.GitBranch;

import java.util.Map;
import java.util.NavigableMap;

/**
 * Resolving repository by URL.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface RepositoryMapping<T extends BranchProvider> extends Shared {
  @Nullable
  static <T extends BranchProvider> RepositoryInfo findRepositoryInfo(@NotNull RepositoryMapping<T> mapping, @NotNull SVNURL url) throws SVNException {
    final String path = StringHelper.normalizeDir(url.getPath());
    final Map.Entry<String, T> repo = mapping.getMapping().floorEntry(path);
    if (repo == null || !StringHelper.isParentPath(repo.getKey(), path))
      return null;

    final String branchPath = repo.getKey().isEmpty() ? path : path.substring(repo.getKey().length() - 1);;
    final Map.Entry<String, GitBranch> branch = repo.getValue().getBranches().floorEntry(branchPath);
    if (branch == null || !StringHelper.isParentPath(branch.getKey(), branchPath))
      return null;

    return new RepositoryInfo(
        SVNURL.create(
            url.getProtocol(),
            url.getUserInfo(),
            url.getHost(),
            url.getPort() == SVNURL.getDefaultPortNumber(url.getProtocol()) ? -1 : url.getPort(),
            repo.getKey() + branch.getKey().substring(1),
            true
        ),
        branch.getValue()
    );
  }

  @NotNull
  NavigableMap<String, T> getMapping();

  @Nullable
  static <T> Map.Entry<String, T> getMapped(@NotNull NavigableMap<String, T> mapping, @NotNull String prefix) {
    final String path = StringHelper.normalizeDir(prefix);
    final Map.Entry<String, T> entry = mapping.floorEntry(path);
    if (entry != null && StringHelper.isParentPath(entry.getKey(), path)) {
      return entry;
    }
    return null;
  }

}
