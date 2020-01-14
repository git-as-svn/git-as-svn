/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.filter.LfsFilter;
import svnserver.ext.gitlfs.storage.LfsStorage;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GitFilters {
  @NotNull
  public final GitFilter raw;
  @NotNull
  public final GitFilter link;
  @NotNull
  private final GitFilter[] filters;

  public GitFilters(@NotNull LocalContext context, @Nullable LfsStorage lfsStorage) {
    this.raw = new GitFilterRaw(context);
    this.link = new GitFilterLink(context);
    this.filters = new GitFilter[]{
        raw,
        link,
        new GitFilterGzip(context),
        new LfsFilter(context, lfsStorage),
    };
  }

  @Nullable
  public GitFilter get(@NotNull String name) {
    for (GitFilter filter : filters)
      if (filter.getName().equals(name))
        return filter;

    return null;
  }
}
