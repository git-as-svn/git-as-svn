package svnserver.replay;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import svnserver.parser.SvnTestServer;

import java.io.File;
import java.net.URL;

/**
 * Replay svn repository to git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ReplayTest {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(ReplayTest.class);

  @Test
  public void testReplay() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final URL repoMark = ReplayTest.class.getResource("repo/format");
      final SVNURL url = SVNURL.fromFile(new File(repoMark.getPath()).getParentFile());
      final SVNRepository srcRepo = SVNRepositoryFactory.create(url);
      final SVNRepository dstRepo = SVNRepositoryFactory.create(server.getUrl());
      dstRepo.setAuthenticationManager(server.getAuthenticator());

      long lastRevision = srcRepo.getLatestRevision();
      log.info("Start replay");
      for (long revision = 2; revision <= lastRevision; revision++) {
        final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
        log.info("  replay commit #{}: {}", revision, message.getString());
        replayRevision(srcRepo, dstRepo, revision);
        log.info("  compare revisions #{}: {}", revision, message.getString());
        compareRevision(srcRepo, dstRepo, revision);
      }
      log.info("End replay");
    }
  }

  private void replayRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
    final ISVNEditor editor = dstRepo.getCommitEditor(message.getString(), null);
    srcRepo.diff(srcRepo.getLocation(), revision, revision - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, revision - 1, SVNDepth.INFINITY, false);
      reporter.finishReport();
    }, new FilterSVNEditor(editor));
  }

  private void compareRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    final ExportSVNEditor srcExport = new ExportSVNEditor();
    srcRepo.diff(srcRepo.getLocation(), revision, revision - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, 0, SVNDepth.INFINITY, true);
      reporter.finishReport();
    }, new FilterSVNEditor(srcExport));

    final ExportSVNEditor dstExport = new ExportSVNEditor();
    dstRepo.diff(srcRepo.getLocation(), revision, revision - 1, null, false, SVNDepth.INFINITY, true, reporter -> {
      reporter.setPath("", null, 0, SVNDepth.INFINITY, true);
      reporter.finishReport();
    }, new FilterSVNEditor(dstExport));

    Assert.assertEquals(srcExport.toString(), dstExport.toString());
  }

}
