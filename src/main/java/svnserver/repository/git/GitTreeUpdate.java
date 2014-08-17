package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.Map;

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
    this.entries = entries;
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
    for (Map.Entry<String, GitTreeEntry> entry : entries.entrySet()) {
      final String name = entry.getKey();
      final GitTreeEntry value = entry.getValue();
      treeBuilder.append(name, value.getFileMode(), value.getObjectId().getObject());
    }
    return inserter.insert(treeBuilder);
  }
}
