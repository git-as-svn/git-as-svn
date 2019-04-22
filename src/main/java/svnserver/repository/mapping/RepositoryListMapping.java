/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.mapping;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.StringHelper;
import svnserver.repository.RepositoryInfo;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.git.GitRepository;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class RepositoryListMapping implements RepositoryMapping {
  @NotNull
  private final NavigableMap<String, GitRepository> mapping;

  public RepositoryListMapping(@NotNull Map<String, GitRepository> mapping) {
    this.mapping = new TreeMap<>();

    mapping.forEach((path, repository) -> {
      RepositoryListMapping.this.mapping.put(StringHelper.normalizeDir(path), repository);
    });
  }

  @Nullable
  @Override
  public RepositoryInfo getRepository(@NotNull SVNURL url) throws SVNException {
    final Map.Entry<String, GitRepository> entry = getMapped(mapping, url.getPath());
    if (entry != null) {
      return new RepositoryInfo(
          SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort() == SVNURL.getDefaultPortNumber(url.getProtocol()) ? -1 : url.getPort(), entry.getKey(), true),
          entry.getValue()
      );
    }
    return null;
  }

  @Nullable
  public static <T> Map.Entry<String, T> getMapped(@NotNull NavigableMap<String, T> mapping, @NotNull String prefix) {
    final String path = StringHelper.normalizeDir(prefix);
    final Map.Entry<String, T> entry = mapping.floorEntry(path);
    if (entry != null && StringHelper.isParentPath(entry.getKey(), path)) {
      return entry;
    }
    return null;
  }
}
