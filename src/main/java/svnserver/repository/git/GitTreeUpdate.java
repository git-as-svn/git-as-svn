package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.*;

/**
 * Git tree updater.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitTreeUpdate {
  @NotNull
  private final String name;
  @NotNull
  private final Map<String, GitTreeEntry> entries;

  public GitTreeUpdate(@NotNull String name, @NotNull Iterable<GitTreeEntry> entries) {
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

  @NotNull
  public Map<String, GitTreeEntry> getEntries() {
    return entries;
  }

  @NotNull
  public ObjectId buildTree(@NotNull ObjectInserter inserter) throws IOException, SVNException {
    final TreeFormatter treeBuilder = new TreeFormatter();
    final List<GitTreeEntry> sortedEntries = new ArrayList<>(entries.values());
    Collections.sort(sortedEntries);
    for (GitTreeEntry entry : sortedEntries) {
      treeBuilder.append(entry.getFileName(), entry.getFileMode(), entry.getObjectId().getObject());
    }
    return inserter.insert(treeBuilder);
  }
}
