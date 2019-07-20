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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.StringHelper;
import svnserver.context.Shared;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.git.BranchProvider;
import svnserver.repository.git.GitBranch;
import svnserver.server.command.BaseCmd;

import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Resolving repository by URL.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface RepositoryMapping<T extends BranchProvider> extends Shared {
  @Nullable
  static <T extends BranchProvider> RepositoryInfo findRepositoryInfo(@NotNull RepositoryMapping<T> mapping, @NotNull SVNURL url, @NotNull SvnServerWriter writer) throws SVNException, IOException {
    final String path = StringHelper.normalizeDir(url.getPath());
    final Map.Entry<String, T> repo = getMapped(mapping.getMapping(), path);
    if (repo == null) {
      BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, "Repository not found: " + url));
      return null;
    }

    final String branchPath = repo.getKey().isEmpty() ? path : path.substring(repo.getKey().length() - 1);
    final NavigableMap<String, GitBranch> branches = repo.getValue().getBranches();

    if (branchPath.length() <= 1) {
      final String branchName = repo.getValue().getBranches().size() == 1
          ? repo.getValue().getBranches().values().iterator().next().getShortBranchName()
          : "<branchname>";
      final String msg = String.format("Repository branch not found. Use `svn relocate %s/%s` to fix your working copy", url, branchName);
      BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, msg));
      return null;
    }

    final Map.Entry<String, GitBranch> branch = getMapped(branches, branchPath);

    if (branch == null) {
      BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, "Repository branch not found: " + url));
      return null;
    }

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

  @Nullable
  static <T> Map.Entry<String, T> getMapped(@NotNull NavigableMap<String, T> mapping, @NotNull String prefix) {
    final String path = StringHelper.normalizeDir(prefix);

    // TODO: this could be must faster if we find an appropriate trie implementation.
    Map.Entry<String, T> result = null;
    for (Map.Entry<String, T> entry : mapping.headMap(path, true).entrySet()) {
      if (!StringHelper.isParentPath(entry.getKey(), path))
        continue;

      if (result == null || entry.getKey().length() > result.getKey().length())
        result = entry;
    }

    return result;
  }

  @NotNull
  NavigableMap<String, T> getMapping();
}
