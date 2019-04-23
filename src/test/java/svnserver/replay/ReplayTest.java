/*
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
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReplayHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.SvnTestServer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * Replay svn repository to git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class ReplayTest {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(ReplayTest.class);

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
        log.info("  replay commit #{}: {}", revision, StringHelper.getFirstLine(message.getString()));
        replayRangeRevision(srcRepo, dstRepo, revision, false);
        log.info("  compare revisions #{}: {}", revision, StringHelper.getFirstLine(message.getString()));
        compareRevision(srcRepo, revision, dstRepo, dstRepo.getLatestRevision());
      }
      log.info("End replay");
    }
  }

  private void replayRangeRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision, boolean checkDelete) throws SVNException {
    final Map<Long, CopyFromSVNEditor> copyFroms = new TreeMap<>();
    srcRepo.replayRange(revision, revision, 0, true, new ISVNReplayHandler() {
      @Override
      public ISVNEditor handleStartRevision(long revision, SVNProperties revisionProperties) throws SVNException {
        final CopyFromSVNEditor editor = new CopyFromSVNEditor(dstRepo.getCommitEditor(revisionProperties.getStringValue("svn:log"), null), "/", checkDelete);
        copyFroms.put(revision, editor);
        return editor;
      }

      @Override
      public void handleEndRevision(long revision, SVNProperties revisionProperties, ISVNEditor editor) throws SVNException {
        editor.closeEdit();
      }
    });
    for (Map.Entry<Long, CopyFromSVNEditor> entry : copyFroms.entrySet()) {
      checkCopyFrom(srcRepo, entry.getValue(), entry.getKey());
    }
  }

  private void compareRevision(@NotNull SVNRepository srcRepo, long srcRev, @NotNull SVNRepository dstRepo, long dstRev) throws SVNException {
    final ExportSVNEditor srcExport = new ExportSVNEditor(true);
    srcRepo.diff(srcRepo.getLocation(), srcRev, srcRev - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, 0, SVNDepth.INFINITY, true);
      reporter.finishReport();
    }, new FilterSVNEditor(srcExport, true));

    final ExportSVNEditor dstExport = new ExportSVNEditor(true);
    dstRepo.diff(dstRepo.getLocation(), dstRev, dstRev - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, 0, SVNDepth.INFINITY, true);
      reporter.finishReport();
    }, new FilterSVNEditor(dstExport, true));

    Assert.assertEquals(srcExport.toString(), dstExport.toString());
  }

  private void checkCopyFrom(@NotNull SVNRepository repo, @NotNull CopyFromSVNEditor editor, long revision) throws SVNException {
    final Map<String, String> copyFrom = new TreeMap<>();
    repo.log(new String[]{""}, revision, revision, true, true, logEntry -> {
      for (SVNLogEntryPath entry : logEntry.getChangedPaths().values()) {
        if (entry.getCopyPath() != null) {
          copyFrom.put(entry.getPath(), entry.getCopyPath() + "@" + entry.getCopyRevision());
        }
      }
    });
    Assert.assertEquals(editor.getCopyFrom(), copyFrom);
  }

  @Ignore("TODO: issue #237")
  @Test
  public void testPartialReplay() throws Exception {
    try (
        SvnTestServer src = SvnTestServer.createMasterRepository();
        SvnTestServer dst = SvnTestServer.createEmpty()
    ) {
      final SVNRepository srcRepo = SvnTestServer.openSvnRepository(src.getUrl().appendPath("src", false), SvnTestServer.USER_NAME, SvnTestServer.PASSWORD);
      final SVNRepository dstRepo = dst.openSvnRepository();

      srcRepo.replayRange(
          1,
          100,
          0,
          true,
          new ISVNReplayHandler() {
            @Override
            public ISVNEditor handleStartRevision(long revision, SVNProperties revisionProperties) throws SVNException {
              return dstRepo.getCommitEditor(revisionProperties.getStringValue(SVNRevisionProperty.LOG), null);
            }

            @Override
            public void handleEndRevision(long revision, SVNProperties revisionProperties, ISVNEditor editor) throws SVNException {
              editor.closeEdit();
            }
          }
      );
    }
  }

  @Test
  public void testReplaySelfWithUpdate() throws Exception {
    checkReplaySelf(this::updateRevision);
  }

  private void checkReplaySelf(@NotNull ReplayMethod replayMethod) throws Exception {
    try (
        SvnTestServer src = SvnTestServer.createMasterRepository();
        SvnTestServer dst = SvnTestServer.createEmpty()
    ) {
      final SVNRepository srcRepo = src.openSvnRepository();
      final SVNRepository dstRepo = dst.openSvnRepository();
      final Repository srcGit = src.getRepository();
      final Repository dstGit = dst.getRepository();

      final long lastRevision = Math.min(200, srcRepo.getLatestRevision());
      log.info("Start replay");
      for (long revision = 1; revision <= lastRevision; revision++) {
        final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
        final SVNPropertyValue srcHash = srcRepo.getRevisionPropertyValue(revision, SvnConstants.PROP_GIT);
        log.info("  replay commit #{} {}: {}", revision, new String(srcHash.getBytes()), StringHelper.getFirstLine(message.getString()));
        replayMethod.replay(srcRepo, dstRepo, revision);
        log.info("  compare revisions #{}: {}", revision, StringHelper.getFirstLine(message.getString()));
        compareRevision(srcRepo, revision, dstRepo, revision);
        final SVNPropertyValue dstHash = dstRepo.getRevisionPropertyValue(revision, SvnConstants.PROP_GIT);
        compareGitRevision(srcGit, srcHash, dstGit, dstHash);
      }
      log.info("End replay");
    }
  }

  private void updateRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
    final CopyFromSVNEditor editor = new CopyFromSVNEditor(dstRepo.getCommitEditor(message.getString(), null), "/", true);
    srcRepo.update(revision, "", SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, revision - 1, SVNDepth.INFINITY, false);
      reporter.finishReport();
    }, new FilterSVNEditor(editor, true));
    checkCopyFrom(srcRepo, editor, revision);
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

  @Test
  public void testReplaySelfWithReplay() throws Exception {
    checkReplaySelf(this::replayRevision);
  }

  private void replayRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    SVNProperties revisionProperties = srcRepo.getRevisionProperties(revision, null);
    CopyFromSVNEditor editor = new CopyFromSVNEditor(dstRepo.getCommitEditor(revisionProperties.getStringValue("svn:log"), null), "/", true);
    srcRepo.replay(revision - 1, revision, true, editor);
    editor.closeEdit();
    checkCopyFrom(srcRepo, editor, revision);
  }

  @Test
  public void testReplaySelfWithReplayRange() throws Exception {
    checkReplaySelf((srcRepo, dstRepo, revision) -> replayRangeRevision(srcRepo, dstRepo, revision, true));
  }

  @FunctionalInterface
  private interface ReplayMethod {
    void replay(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException;
  }

}
