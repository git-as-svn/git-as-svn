/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

/**
 * Visitor for Depth enumeration.
 *
 * @author a.navrotskiy
 */
public interface DepthVisitor<R> {
  @NotNull
  R visitEmpty() throws SVNException;

  @NotNull
  R visitFiles() throws SVNException;

  @NotNull
  R visitImmediates() throws SVNException;

  @NotNull
  R visitInfinity() throws SVNException;

  @NotNull
  R visitUnknown();
}
