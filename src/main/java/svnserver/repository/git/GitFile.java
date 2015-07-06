/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsFile;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.prop.GitProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface GitFile extends GitEntry, VcsFile {
  @NotNull
  @Override
  default GitEntry createChild(@NotNull String name, boolean isDir) {
    return new GitEntryImpl(getRawProperties(), getFullPath(), GitProperty.emptyArray, name, isDir ? FileMode.TREE : FileMode.REGULAR_FILE);
  }

  @NotNull
  GitRepository getRepo();

  int getRevision();

  @Nullable
  GitFilter getFilter();

  @NotNull
  @Override
  GitProperty[] getRawProperties();

  @Nullable
  GitTreeEntry getTreeEntry();

  @Nullable
  GitObject<ObjectId> getObjectId();

  @NotNull
  @Override
  default Map<String, String> getProperties() throws IOException, SVNException {
    return getUpstreamProperties();
  }

  @NotNull
  default Map<String, String> getUpstreamProperties() {
    final Map<String, String> result = new HashMap<>();
    for (GitProperty prop : getRawProperties()) {
      prop.apply(result);
    }
    return result;
  }

  @NotNull
  @Override
  default Map<String, String> getRevProperties() throws IOException {
    final Map<String, String> props = new HashMap<>();
    final GitRevision last = getLastChange();
    props.put(SVNProperty.UUID, getRepo().getUuid());
    props.put(SVNProperty.COMMITTED_REVISION, String.valueOf(last.getId()));
    putProperty(props, SVNProperty.COMMITTED_DATE, last.getDateString());
    putProperty(props, SVNProperty.LAST_AUTHOR, last.getAuthor());
    return props;
  }

  @NotNull
  @Override
  String getMd5() throws IOException, SVNException;

  @NotNull
  @Override
  String getContentHash() throws IOException, SVNException;

  @Override
  long getSize() throws IOException, SVNException;

  @NotNull
  @Override
  InputStream openStream() throws IOException, SVNException;

  @NotNull
  @Override
  default SVNNodeKind getKind() {
    final int objType = getFileMode().getObjectType();
    switch (objType) {
      case Constants.OBJ_TREE:
      case Constants.OBJ_COMMIT:
        return SVNNodeKind.DIR;
      case Constants.OBJ_BLOB:
        return SVNNodeKind.FILE;
      default:
        throw new IllegalStateException("Unknown obj type: " + objType);
    }
  }

  @NotNull
  FileMode getFileMode();

  @NotNull
  @Override
  Iterable<GitFile> getEntries() throws IOException, SVNException;

  @NotNull
  @Override
  default GitRevision getLastChange() throws IOException {
    final GitRepository repo = getRepo();
    final int lastChange = repo.getLastChange(getFullPath(), getRevision());
    if (lastChange < 0) {
      throw new IllegalStateException("Internal error: can't find lastChange revision for file: " + getFileName() + "@" + getRevision());
    }
    return repo.sureRevisionInfo(lastChange);
  }

  @Nullable
  @Override
  VcsCopyFrom getCopyFrom() throws IOException;

  @Nullable
  GitFile getEntry(@NotNull String name) throws IOException, SVNException;

  static void putProperty(@NotNull Map<String, String> props, @NotNull String name, @Nullable String value) {
    if (value != null) {
      props.put(name, value);
    }
  }
}
