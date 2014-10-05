/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReplayHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import svnserver.SvnConstants;
import svnserver.SvnTestServer;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Replay svn repository to git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ReplayTest {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(ReplayTest.class);

  @FunctionalInterface
  private interface ReplayMethod {
    void replay(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException;
  }

  @Test
  public void testReplayFileModification() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final URL repoMark = ReplayTest.class.getResource("repo/format");
      final SVNURL url = SVNURL.fromFile(new File(repoMark.getPath()).getParentFile());
      final SVNRepository srcRepo = SVNRepositoryFactory.create(url);
      final SVNRepository dstRepo = server.openSvnRepository();

      long lastRevision = srcRepo.getLatestRevision();
      log.info("Start replay");
      for (long revision = 1; revision <= lastRevision; revision++) {
        final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
        log.info("  replay commit #{}: {}", revision, message.getString());
        replayRangeRevision(srcRepo, dstRepo, revision);
        log.info("  compare revisions #{}: {}", revision, message.getString());
        compareRevision(srcRepo, revision, dstRepo, dstRepo.getLatestRevision());
      }
      log.info("End replay");
    }
  }

  @Test
  public void testReplaySelfWithUpdate() throws Exception {
    checkReplaySelf(this::updateRevision);
  }

  @Test
  public void testReplaySelfWithReplay() throws Exception {
    checkReplaySelf(this::replayRevision);
  }

  @Test
  public void testReplaySelfWithReplayRange() throws Exception {
    checkReplaySelf(this::replayRangeRevision);
  }

  @Test
  public void checkReplaySelf(@NotNull ReplayMethod replayMethod) throws Exception {
    try (
        SvnTestServer src = SvnTestServer.createMasterRepository();
        SvnTestServer dst = SvnTestServer.createEmpty()
    ) {
      final SVNRepository srcRepo = src.openSvnRepository();
      final SVNRepository dstRepo = dst.openSvnRepository();
      final Repository srcGit = src.getRepository();
      final Repository dstGit = dst.getRepository();

      long lastRevision = srcRepo.getLatestRevision();
      log.info("Start replay");
      for (long revision = 1; revision <= lastRevision; revision++) {
        final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
        final SVNPropertyValue srcHash = srcRepo.getRevisionPropertyValue(revision, SvnConstants.PROP_GIT);
        log.info("  replay commit #{} {}: {}", revision, new String(srcHash.getBytes()), message.getString());
        replayMethod.replay(srcRepo, dstRepo, revision);
        log.info("  compare revisions #{}: {}", revision, message.getString());
        compareRevision(srcRepo, revision, dstRepo, revision);
        final SVNPropertyValue dstHash = dstRepo.getRevisionPropertyValue(revision, SvnConstants.PROP_GIT);
        compareGitRevision(srcGit, srcHash, dstGit, dstHash);
      }
      log.info("End replay");
    }
  }

  private void compareGitRevision(@NotNull Repository srcGit, @NotNull SVNPropertyValue srcHash, @NotNull Repository dstGit, @NotNull SVNPropertyValue dstHash) throws IOException {
    final RevCommit srcCommit = getCommit(srcGit, srcHash);
    final RevCommit dstCommit = getCommit(dstGit, dstHash);
    Assert.assertEquals(srcCommit.getTree().getName(), dstCommit.getTree().getName());
  }

  @NotNull
  private RevCommit getCommit(@NotNull Repository git, @NotNull SVNPropertyValue hash) throws IOException {
    return new RevWalk(git).parseCommit(ObjectId.fromString(new String(hash.getBytes())));
  }

  private void replayRangeRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    srcRepo.replayRange(revision, revision, -1, true, new ISVNReplayHandler() {
      @Override
      public ISVNEditor handleStartRevision(long revision, SVNProperties revisionProperties) throws SVNException {
        return dstRepo.getCommitEditor(revisionProperties.getStringValue("svn:log"), null);
      }

      @Override
      public void handleEndRevision(long revision, SVNProperties revisionProperties, ISVNEditor editor) throws SVNException {
        editor.closeEdit();
      }
    });
  }

  private void replayRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    SVNProperties revisionProperties = srcRepo.getRevisionProperties(revision, null);
    ISVNEditor editor = dstRepo.getCommitEditor(revisionProperties.getStringValue("svn:log"), null);
    srcRepo.replay(revision - 1, revision, true, editor);
    editor.closeEdit();
  }

  private void updateRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
    final ISVNEditor editor = dstRepo.getCommitEditor(message.getString(), null);
    srcRepo.diff(srcRepo.getLocation(), revision, revision - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, revision - 1, SVNDepth.INFINITY, false);
      reporter.finishReport();
    }, new FilterSVNEditor(editor));
  }

  private void compareRevision(@NotNull SVNRepository srcRepo, long srcRev, @NotNull SVNRepository dstRepo, long dstRev) throws SVNException {
    final ExportSVNEditor srcExport = new ExportSVNEditor();
    srcRepo.diff(srcRepo.getLocation(), srcRev, srcRev - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, 0, SVNDepth.INFINITY, true);
      reporter.finishReport();
    }, new FilterSVNEditor(srcExport));

    final ExportSVNEditor dstExport = new ExportSVNEditor();
    dstRepo.diff(dstRepo.getLocation(), dstRev, dstRev - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, 0, SVNDepth.INFINITY, true);
      reporter.finishReport();
    }, new FilterSVNEditor(dstExport));

    Assert.assertEquals(srcExport.toString(), dstExport.toString());
  }

}
