package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import svnserver.StringHelper;
import svnserver.SvnConstants;
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
  @Nullable
  private final ObjectId objectId;
  @Nullable
  private final RevCommit commit;
  @NotNull
  private final Map<String, GitLogEntry> changes;

  private final int revision;

  public GitRevision(@NotNull GitRepository repo, int revision, @NotNull Map<String, GitLogEntry> changes, @Nullable RevCommit commit) {
    this.repo = repo;
    this.revision = revision;
    this.changes = changes;
    this.objectId = commit != null ? commit.getId() : null;
    this.commit = revision > 0 ? commit : null;
  }

  @Override
  public int getId() {
    return revision;
  }

  @Nullable
  public ObjectId getObjectId() {
    return objectId;
  }

  @Nullable
  public RevCommit getCommit() {
    return commit;
  }

  @NotNull
  @Override
  public Map<String, GitLogEntry> getChanges() {
    return changes;
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() {
    final Map<String, String> props = new HashMap<>();
    putProperty(props, SVNRevisionProperty.AUTHOR, getAuthor());
    putProperty(props, SVNRevisionProperty.LOG, getLog());
    putProperty(props, SVNRevisionProperty.DATE, getDate());
    if (commit != null) {
      props.put(SvnConstants.PROP_GIT, commit.name());
    }
    return props;
  }

  private void putProperty(@NotNull Map<String, String> props, @NotNull String name, @Nullable String value) {
    if (value != null) {
      props.put(name, value);
    }
  }

  @Nullable
  @Override
  public String getDate() {
    return commit == null ? null : StringHelper.formatDate(TimeUnit.SECONDS.toMillis(commit.getCommitTime()));
  }

  @Nullable
  @Override
  public String getAuthor() {
    return commit == null ? null : commit.getCommitterIdent().getName();
  }

  @Nullable
  @Override
  public String getLog() {
    return commit == null ? null : commit.getFullMessage().trim();
  }

  @Nullable
  @Override
  public GitFile getFile(@NotNull String fullPath) throws IOException, SVNException {
    if (commit == null) {
      return new GitFile(repo, null, "", GitProperty.emptyArray, revision);
    }
    GitFile result = new GitFile(repo, commit, revision);
    for (String pathItem : fullPath.split("/")) {
      if (pathItem.isEmpty()) {
        continue;
      }
      result = result.getEntry(pathItem);
      if (result == null) {
        return null;
      }
    }
    return result;
  }
}
