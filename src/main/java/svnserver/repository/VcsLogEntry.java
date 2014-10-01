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

/**
 * File modification information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsLogEntry {
  char getChange() throws IOException, SVNException;

  @NotNull
  SVNNodeKind getKind();

  boolean isContentModified() throws IOException, SVNException;

  boolean isPropertyModified() throws IOException, SVNException;

  boolean isModified() throws IOException, SVNException;

  @Nullable
  VcsCopyFrom getCopyFrom();
}
