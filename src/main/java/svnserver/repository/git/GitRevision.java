/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.repository.VcsCopyFrom;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Git revision.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitRevision {
  @NotNull
  private final GitRepository repo;
  @NotNull
  private final ObjectId cacheCommit;
  @Nullable
  private final RevCommit gitOldCommit;
  @Nullable
  private final RevCommit gitNewCommit;

  @NotNull
  private final Map<String, VcsCopyFrom> renames;
  private final long date;
  private final int revision;

  GitRevision(@NotNull GitRepository repo,
              @NotNull ObjectId cacheCommit,
              int revision,
              @NotNull Map<String, VcsCopyFrom> renames,
              @Nullable RevCommit gitOldCommit,
              @Nullable RevCommit gitNewCommit,
              int commitTimeSec) {
    this.repo = repo;
    this.cacheCommit = cacheCommit;
    this.revision = revision;
    this.renames = renames;
    this.gitOldCommit = gitOldCommit;
    this.gitNewCommit = gitNewCommit;
    this.date = TimeUnit.SECONDS.toMillis(commitTimeSec);
  }

  @NotNull ObjectId getCacheCommit() {
    return cacheCommit;
  }

  public int getId() {
    return revision;
  }

  @NotNull
  public Map<String, String> getProperties(boolean includeInternalProps) {
    final Map<String, String> props = new HashMap<>();
    if (includeInternalProps) {
      putProperty(props, SVNRevisionProperty.AUTHOR, getAuthor());
      putProperty(props, SVNRevisionProperty.LOG, getLog());
      putProperty(props, SVNRevisionProperty.DATE, getDateString());
    }
    if (gitNewCommit != null) {
      props.put(SvnConstants.PROP_GIT, gitNewCommit.name());
    }
    return props;
  }

  private void putProperty(@NotNull Map<String, String> props, @NotNull String name, @Nullable String value) {
    if (value != null) {
      props.put(name, value);
    }
  }

  @Nullable
  public String getAuthor() {
    if (gitNewCommit == null)
      return null;

    final PersonIdent ident = gitNewCommit.getAuthorIdent();
    return String.format("%s <%s>", ident.getName(), ident.getEmailAddress());
  }

  @Nullable
  public String getLog() {
    return gitNewCommit == null ? null : gitNewCommit.getFullMessage().trim();
  }

  @NotNull
  public String getDateString() {
    return StringHelper.formatDate(getDate());
  }

  public long getDate() {
    return date;
  }

  @Nullable
  public GitFile getFile(@NotNull String fullPath) throws IOException, SVNException {
    if (gitNewCommit == null) {
      if (fullPath.isEmpty())
        return new GitFileEmptyTree(repo, "", revision);
      else
        return null;
    }
    GitFile result = GitFileTreeEntry.create(repo, gitNewCommit.getTree(), revision);
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

  @NotNull
  public Map<String, GitLogEntry> getChanges() throws IOException, SVNException {
    if (gitNewCommit == null) {
      return Collections.emptyMap();
    }
    final GitFile oldTree = gitOldCommit == null ? new GitFileEmptyTree(repo, "", revision - 1) : GitFileTreeEntry.create(repo, gitOldCommit.getTree(), revision - 1);
    final GitFile newTree = GitFileTreeEntry.create(repo, gitNewCommit.getTree(), revision);

    return ChangeHelper.collectChanges(oldTree, newTree, false);
  }

  @Nullable
  public VcsCopyFrom getCopyFrom(@NotNull String fullPath) {
    return renames.get(fullPath);
  }

  @Nullable RevCommit getGitNewCommit() {
    return gitNewCommit;
  }
}
