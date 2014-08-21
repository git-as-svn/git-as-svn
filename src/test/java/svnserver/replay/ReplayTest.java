package svnserver.replay;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import svnserver.parser.SvnTestServer;

import java.io.File;
import java.io.OutputStream;
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
        replayRevision(srcRepo, dstRepo, revision);
      }
      log.info("End replay");
    }
  }

  private void replayRevision(@NotNull SVNRepository srcRepo, @NotNull SVNRepository dstRepo, long revision) throws SVNException {
    final SVNPropertyValue message = srcRepo.getRevisionPropertyValue(revision, "svn:log");
    final ISVNEditor editor = dstRepo.getCommitEditor(message.getString(), null);
    log.info("  replay commit #{}: {}", revision, message.getString());
    srcRepo.diff(srcRepo.getLocation(), revision, revision - 1, null, false, SVNDepth.INFINITY, true, new ISVNReporterBaton() {
      @Override
      public void report(ISVNReporter reporter) throws SVNException {
        reporter.setPath("", null, revision - 1, SVNDepth.INFINITY, false);
        reporter.finishReport();
      }
    }, new FilterSVNEditor(editor));

  }

  private static class FilterSVNEditor implements ISVNEditor {
    @NotNull
    private final ISVNEditor editor;

    public FilterSVNEditor(@NotNull ISVNEditor editor) {
      this.editor = editor;
    }

    @Override
    public void targetRevision(long revision) throws SVNException {
      editor.targetRevision(revision);
    }

    @Override
    public void openRoot(long revision) throws SVNException {
      editor.openRoot(revision);
    }

    @Override
    public void deleteEntry(String path, long revision) throws SVNException {
      editor.deleteEntry(path, revision);
    }

    @Override
    public void absentDir(String path) throws SVNException {
      editor.absentDir(path);
    }

    @Override
    public void absentFile(String path) throws SVNException {
      editor.absentFile(path);
    }

    @Override
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
      editor.addDir(path, copyFromPath, copyFromRevision);
    }

    @Override
    public void openDir(String path, long revision) throws SVNException {
      editor.openDir(path, revision);
    }

    @Override
    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
      if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
        editor.changeDirProperty(name, value);
      }
    }

    @Override
    public void closeDir() throws SVNException {
      editor.closeDir();
    }

    @Override
    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
      editor.addFile(path, copyFromPath, copyFromRevision);
    }

    @Override
    public void openFile(String path, long revision) throws SVNException {
      editor.openFile(path, revision);
    }

    @Override
    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
      if (!propertyName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
        editor.changeFileProperty(path, propertyName, propertyValue);
      }
    }

    @Override
    public void closeFile(String path, String textChecksum) throws SVNException {
      editor.closeFile(path, textChecksum);
    }

    @Override
    public SVNCommitInfo closeEdit() throws SVNException {
      return editor.closeEdit();
    }

    @Override
    public void abortEdit() throws SVNException {
      editor.abortEdit();
    }

    @Override
    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
      editor.applyTextDelta(path, baseChecksum);
    }

    @Override
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
      return editor.textDeltaChunk(path, diffWindow);
    }

    @Override
    public void textDeltaEnd(String path) throws SVNException {
      editor.textDeltaEnd(path);
    }
  }
}
