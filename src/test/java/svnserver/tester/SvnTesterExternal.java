/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.tester;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * External subversion server for testing.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnTesterExternal implements SvnTester {
  @NotNull
  private final SVNURL url;
  @Nullable
  private final ISVNAuthenticationManager authManager;
  @NotNull
  private final String suffix;

  public SvnTesterExternal(@NotNull SVNURL url, @Nullable ISVNAuthenticationManager authManager) throws SVNException {
    this.url = url;
    this.authManager = authManager;
    this.suffix = UUID.randomUUID().toString();
    final SVNRepository repo = openSvnRepository(url);
    try {
      final ISVNEditor editor = repo.getCommitEditor("Create subdir for test", null, false, null);
      editor.openRoot(-1);
      editor.addDir(suffix, null, -1);
      editor.closeDir();
      editor.closeEdit();
    } finally {
      repo.closeSession();
    }
  }

  @NotNull
  @Override
  public SVNURL getUrl() throws SVNException {
    return url.appendPath(suffix, false);
  }

  @NotNull
  @Override
  public SVNRepository openSvnRepository() throws SVNException {
    return openSvnRepository(url.appendPath(suffix, false));
  }

  @Override
  public void close() throws Exception {
    final SVNRepository repo = openSvnRepository(url);
    long revision = repo.getLatestRevision();
    try {
      final SVNLock[] locks = repo.getLocks(suffix);
      if (locks.length > 0) {
        final SVNURL root = repo.getRepositoryRoot(true);
        final Map<String, String> locksMap = new HashMap<>();
        for (SVNLock lock : locks) {
          final String relativePath = SVNURLUtil.getRelativeURL(url, root.appendPath(lock.getPath(), false), false);
          locksMap.put(relativePath, lock.getID());
        }
        repo.unlock(locksMap, true, null);
      }
      final ISVNEditor editor = repo.getCommitEditor("Remove subdir for test", null, false, null);
      editor.openRoot(-1);
      editor.deleteEntry(suffix, revision);
      editor.closeEdit();
    } finally {
      repo.closeSession();
    }
  }

  @NotNull
  private SVNRepository openSvnRepository(@NotNull SVNURL url) throws SVNException {
    final SVNRepository repo = SVNRepositoryFactory.create(url);
    if (authManager != null) {
      repo.setAuthenticationManager(authManager);
    }
    return repo;
  }
}
