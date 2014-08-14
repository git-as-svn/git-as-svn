package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Git tree updater.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitTreeUpdate {
  @NotNull
  private final String name;
  @NotNull
  private final Map<String, GitTreeEntry> entries = new TreeMap<>();

  public GitTreeUpdate(@NotNull String name) throws IOException {
    this.name = name;
  }

  public GitTreeUpdate(@NotNull String name, @NotNull ObjectReader reader, @NotNull ObjectId originalTreeId) throws IOException {
    this.name = name;
    CanonicalTreeParser treeParser = new CanonicalTreeParser(GitRepository.emptyBytes, reader, originalTreeId);
    while (!treeParser.eof()) {
      entries.put(treeParser.getEntryPathString(), new GitTreeEntry(
          treeParser.getEntryFileMode(),
          treeParser.getEntryObjectId()
      ));
      treeParser.next();
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
    for (Map.Entry<String, GitTreeEntry> entry : entries.entrySet()) {
      final String name = entry.getKey();
      final GitTreeEntry value = entry.getValue();
      treeBuilder.append(name, value.getFileMode(), value.getObjectId());
    }
    return inserter.insert(treeBuilder);
  }
}
