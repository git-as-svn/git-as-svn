package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.repository.VcsLogEntry;
import svnserver.repository.VcsRevision;
import svnserver.repository.git.prop.GitProperty;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
  private final Map<String, VcsLogEntry> changes;

  private final int revision;

  public GitRevision(@NotNull GitRepository repo, int revision, @NotNull Map<String, VcsLogEntry> changes, @NotNull RevCommit commit) {
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
  public Map<String, VcsLogEntry> getChanges() {
    return changes;
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
    GitTreeEntry entry = new GitTreeEntry(FileMode.TREE, new GitObject<>(repo.getRepository(), commit.getTree()));
    GitFile result = new GitFile(repo, entry, "", GitProperty.emptyArray, revision);
    for (String pathItem : fullPath.split("/")) {
      if (pathItem.isEmpty()) {
        continue;
      }
      result = result.getEntries().get(pathItem);
      if (result == null) {
        return null;
      }
    }
    return result;
  }
}
