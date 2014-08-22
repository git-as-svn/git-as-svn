package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
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

  public GitTreeUpdate(@NotNull String name, @NotNull Map<String, GitTreeEntry> entries) {
    this.name = name;
    this.entries = new HashMap<>(entries);
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
    final List<Map.Entry<String, GitTreeEntry>> sortedEntries = new ArrayList<>(entries.entrySet());
    Collections.sort(sortedEntries, (o1, o2) -> {
      final String name1 = o1.getKey() + (o1.getValue().getFileMode() == FileMode.TREE ? "/" : "");
      final String name2 = o2.getKey() + (o2.getValue().getFileMode() == FileMode.TREE ? "/" : "");
      return name1.compareTo(name2);
    });
    for (Map.Entry<String, GitTreeEntry> entry : sortedEntries) {
      final String name = entry.getKey();
      final GitTreeEntry value = entry.getValue();
      treeBuilder.append(name, value.getFileMode(), value.getObjectId().getObject());
    }
    return inserter.insert(treeBuilder);
  }
}
