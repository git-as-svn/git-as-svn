/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * Git tree updater.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitTreeUpdate {
  @NotNull
  private final String name;
  @NotNull
  private final Map<String, GitTreeEntry> entries;

  GitTreeUpdate(@NotNull String name, @NotNull Iterable<GitTreeEntry> entries) {
    this.name = name;
    this.entries = new HashMap<>();
    for (GitTreeEntry entry : entries) {
      this.entries.put(entry.getFileName(), entry);
    }
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull Map<String, GitTreeEntry> getEntries() {
    return entries;
  }

  @NotNull ObjectId buildTree(@NotNull ObjectInserter inserter) throws IOException {
    final TreeFormatter treeBuilder = new TreeFormatter();
    final List<GitTreeEntry> sortedEntries = new ArrayList<>(entries.values());
    Collections.sort(sortedEntries);
    for (GitTreeEntry entry : sortedEntries) {
      treeBuilder.append(entry.getFileName(), entry.getFileMode(), entry.getObjectId().getObject());
    }
    new ObjectChecker().checkTree(treeBuilder.toByteArray());
    return inserter.insert(treeBuilder);
  }
}
