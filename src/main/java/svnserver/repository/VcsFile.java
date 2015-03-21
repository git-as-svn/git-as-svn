/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Информация о файле.
 *
 * @author a.navrotskiy
 */
public interface VcsFile {
  @NotNull
  String getFileName();

  @NotNull
  String getFullPath();

  @NotNull
  Map<String, String> getProperties() throws IOException, SVNException;

  @NotNull
  Map<String, String> getRevProperties() throws IOException, SVNException;

  @NotNull
  default Map<String, String> getAllProperties() throws IOException, SVNException {
    Map<String, String> props = new HashMap<>();
    props.putAll(getRevProperties());
    props.putAll(getProperties());
    return props;
  }

  @NotNull
  String getMd5() throws IOException, SVNException;

  /**
   * Get native repository content hash for cheap content modification check.
   */
  @NotNull
  default String getContentHash() throws IOException, SVNException {
    return getMd5();
  }

  long getSize() throws IOException, SVNException;

  @NotNull
  InputStream openStream() throws IOException;

  boolean isDirectory();

  @NotNull
  SVNNodeKind getKind() throws IOException;

  @NotNull
  Iterable<? extends VcsFile> getEntries() throws IOException, SVNException;

  @Nullable
  VcsFile getEntry(@NotNull String name) throws IOException, SVNException;

  @NotNull
  VcsRevision getLastChange() throws IOException;

  @Nullable
  VcsCopyFrom getCopyFrom() throws IOException;
}
