package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import svnserver.SvnConstants;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsRevision;
import svnserver.repository.git.prop.GitProperty;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
  @Nullable
  private final ObjectId objectId;
  @Nullable
  private final RevCommit commit;
  @Nullable
  private final RevCommit prevCommit;
  @NotNull
  private final Map<String, VcsCopyFrom> renames;

  private final long date;
  private final int revision;

  public GitRevision(@NotNull GitRepository repo, int revision, @NotNull Map<String, VcsCopyFrom> renames, @Nullable RevCommit prevCommit, @Nullable RevCommit commit, int commitTimeSec) {
    this.repo = repo;
    this.revision = revision;
    this.renames = renames;
    this.objectId = commit != null ? commit.getId() : null;
    this.date = TimeUnit.SECONDS.toMillis(commitTimeSec);
    this.commit = revision > 0 ? commit : null;
    this.prevCommit = prevCommit;
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
  public Map<String, GitLogEntry> getChanges() throws IOException, SVNException {
    if (commit == null) {
      return Collections.emptyMap();
    }
    final GitFile oldTree = prevCommit == null ? new GitFile(repo, null, "", GitProperty.emptyArray, revision - 1) : new GitFile(repo, prevCommit, revision - 1);
    final GitFile newTree = new GitFile(repo, commit, revision);

    final Map<String, GitLogEntry> changes = new TreeMap<>();
    for (Map.Entry<String, GitLogPair> entry : ChangeHelper.collectChanges(oldTree, newTree, false).entrySet()) {
      changes.put(entry.getKey(), new GitLogEntry(entry.getValue(), renames));
    }
    return changes;
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() {
    final Map<String, String> props = new HashMap<>();
    putProperty(props, SVNRevisionProperty.AUTHOR, getAuthor());
    putProperty(props, SVNRevisionProperty.LOG, getLog());
    putProperty(props, SVNRevisionProperty.DATE, getDateString());
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

  @Override
  public long getDate() {
    return date;
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

  @Nullable
  @Override
  public VcsCopyFrom getCopyFrom(@NotNull String fullPath) {
    return renames.get(fullPath);
  }
}
