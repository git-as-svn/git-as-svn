/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;

import java.io.IOException;
import java.util.*;

/**
 * Class for collecting changes in revision.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class ChangeHelper {
  private ChangeHelper() {
  }

  @NotNull
  public static Map<String, GitLogPair> collectChanges(@Nullable GitFile oldTree, @NotNull GitFile newTree, boolean fullRemoved) throws IOException, SVNException {
    final Map<String, GitLogPair> changes = new HashMap<>();
    final GitLogPair logEntry = new GitLogPair(oldTree, newTree);
    if (oldTree == null || logEntry.isModified()) {
      changes.put("/", logEntry);
    }
    final Queue<TreeCompareEntry> queue = new ArrayDeque<>();
    queue.add(new TreeCompareEntry("", oldTree, newTree));
    while (!queue.isEmpty()) {
      collectChanges(changes, queue, queue.remove(), fullRemoved);
    }
    return changes;
  }

  private static void collectChanges(@NotNull Map<String, GitLogPair> changes, Queue<TreeCompareEntry> queue, @NotNull TreeCompareEntry compareEntry, boolean fullRemoved) throws IOException, SVNException {
    for (GitLogPair pair : compareEntry) {
      final GitFile newEntry = pair.getNewEntry();
      final GitFile oldEntry = pair.getOldEntry();
      if (newEntry == null && oldEntry == null) {
        throw new IllegalStateException();
      }
      if (newEntry != null) {
        if (!newEntry.equals(oldEntry)) {
          final String fullPath = StringHelper.joinPath(compareEntry.path, newEntry.getFileName());
          if (newEntry.isDirectory()) {
            final GitLogPair oldChange = changes.put(fullPath, pair);
            if (oldChange != null) {
              changes.put(fullPath, new GitLogPair(oldChange.getOldEntry(), newEntry));
            }
            queue.add(new TreeCompareEntry(fullPath, ((oldEntry != null) && oldEntry.isDirectory()) ? oldEntry : null, newEntry));
          } else if (oldEntry == null || pair.isModified()) {
            final GitLogPair oldChange = changes.put(fullPath, pair);
            if (oldChange != null) {
              changes.put(fullPath, new GitLogPair(oldChange.getOldEntry(), newEntry));
            }
          }
        }
      } else {
        final String fullPath = StringHelper.joinPath(compareEntry.path, oldEntry.getFileName());
        final GitLogPair oldChange = changes.put(fullPath, pair);
        if (oldChange != null) {
          changes.put(fullPath, new GitLogPair(oldEntry, oldChange.getNewEntry()));
        }
      }
      if (fullRemoved && oldEntry != null && oldEntry.isDirectory()) {
        final String fullPath = StringHelper.joinPath(compareEntry.path, oldEntry.getFileName());
        if (newEntry == null || (!newEntry.isDirectory())) {
          queue.add(new TreeCompareEntry(fullPath, oldEntry, null));
        }
      }
    }
  }

  private static class TreeCompareEntry implements Iterable<GitLogPair> {
    @NotNull
    private final String path;
    @NotNull
    private final Iterable<GitFile> oldTree;
    @NotNull
    private final Iterable<GitFile> newTree;

    private TreeCompareEntry(@NotNull String path, @Nullable GitFile oldTree, @Nullable GitFile newTree) throws IOException, SVNException {
      this.path = path;
      this.oldTree = getIterable(oldTree);
      this.newTree = getIterable(newTree);
    }

    @NotNull
    private static Iterable<GitFile> getIterable(@Nullable GitFile tree) throws IOException, SVNException {
      return tree != null ? tree.getEntries() : Collections.emptyList();
    }

    @Override
    public Iterator<GitLogPair> iterator() {
      return new LogPairIterator(oldTree, newTree);
    }
  }

  private static class LogPairIterator implements Iterator<GitLogPair> {
    @NotNull
    private final Iterator<GitFile> oldIter;
    @NotNull
    private final Iterator<GitFile> newIter;
    @Nullable
    private GitFile oldItem;
    @Nullable
    private GitFile newItem;

    private LogPairIterator(@NotNull Iterable<GitFile> oldTree, @NotNull Iterable<GitFile> newTree) {
      oldIter = oldTree.iterator();
      newIter = newTree.iterator();
      oldItem = nextItem(oldIter);
      newItem = nextItem(newIter);
    }

    @Nullable
    private static GitFile nextItem(@NotNull Iterator<GitFile> iter) {
      return iter.hasNext() ? iter.next() : null;
    }

    @Override
    public boolean hasNext() {
      return (oldItem != null) || (newItem != null);
    }

    @Override
    public GitLogPair next() {
      final int compare;
      if (newItem == null) {
        compare = -1;
      } else if (oldItem == null) {
        compare = 1;
      } else {
        final GitTreeEntry oldTreeEntry = oldItem.getTreeEntry();
        final GitTreeEntry newTreeEntry = newItem.getTreeEntry();
        if (oldTreeEntry == null || newTreeEntry == null) {
          throw new IllegalStateException("Tree entry can be null only for revision tree root.");
        }
        compare = oldItem.getTreeEntry().compareTo(newItem.getTreeEntry());
      }
      final GitFile oldEntry;
      final GitFile newEntry;
      if (compare <= 0) {
        oldEntry = oldItem;
        oldItem = nextItem(oldIter);
      } else {
        oldEntry = null;
      }
      if (compare >= 0) {
        newEntry = newItem;
        newItem = nextItem(newIter);
      } else {
        newEntry = null;
      }
      return new GitLogPair(oldEntry, newEntry);
    }
  }
}
