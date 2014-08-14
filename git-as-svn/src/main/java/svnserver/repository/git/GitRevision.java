package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.repository.VcsLogEntry;
import svnserver.repository.VcsRevision;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Git revision.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitRevision implements VcsRevision {
  @NotNull
  private final GitRepository repo;
  @NotNull
  private final RevCommit commit;
  @NotNull
  private final TreeMap<String, GitLogEntry> changes;

  private final int revision;

  public GitRevision(@NotNull GitRepository repo, int revision, @NotNull TreeMap<String, GitLogEntry> changes, @NotNull RevCommit commit) {
    this.repo = repo;
    this.revision = revision;
    this.changes = changes;
    this.commit = commit;
  }

  @Override
  public int getId() {
    return revision;
  }

  @NotNull
  public RevCommit getCommit() {
    return commit;
  }

  @NotNull
  @Override
  public Map<String, VcsLogEntry> getChanges(@NotNull Set<String> targetPaths) {
    final Map<String, VcsLogEntry> result = new TreeMap<>();
    for (String targetPath : targetPaths) {
      for (Map.Entry<String, GitLogEntry> entry : changes.tailMap(targetPath, true).entrySet()) {
        if (!entry.getKey().startsWith(targetPath)) {
          break;
        }
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() {
    final Map<String, String> props = new HashMap<>();
    props.put(SVNRevisionProperty.AUTHOR, getAuthor());
    props.put(SVNRevisionProperty.LOG, getLog());
    props.put(SVNRevisionProperty.DATE, getDate());
    props.put(SvnConstants.PROP_GIT, commit.name());
    return props;
  }

  @NotNull
  @Override
  public String getDate() {
    return StringHelper.formatDate(TimeUnit.SECONDS.toMillis(commit.getCommitTime()));
  }

  @NotNull
  @Override
  public String getAuthor() {
    return commit.getCommitterIdent().getName();
  }

  @NotNull
  @Override
  public String getLog() {
    return commit.getFullMessage().trim();
  }

  @Nullable
  @Override
  public GitFile getFile(@NotNull String fullPath) throws IOException {
    if (fullPath.isEmpty()) {
      return new GitFile(repo, commit.getTree(), FileMode.TREE, fullPath, revision);
    }
    final TreeWalk treeWalk = TreeWalk.forPath(repo.getRepository(), fullPath.substring(1), commit.getTree());
    if (treeWalk == null) {
      return null;
    }
    return new GitFile(repo, treeWalk.getObjectId(0), treeWalk.getFileMode(0), fullPath, revision);
  }
}
