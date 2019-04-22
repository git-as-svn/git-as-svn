/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.git.GitFile;
import svnserver.repository.git.GitRepository;
import svnserver.repository.git.GitRevision;
import svnserver.server.SessionContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 *   get-file-revs
 *     params:   ( path:string [ start-rev:number ] [ end-rev:number ]
 *                 ? include-merged-revisions:bool )
 *     Before sending response, server sends file-rev entries, ending with "done".
 *     file-rev: ( path:string rev:number rev-props:proplist
 *                 file-props:propdelta ? merged-revision:bool )
 *               | done
 *     After each file-rev, the file delta is sent as one or more strings,
 *     terminated by the empty string.  If there is no delta, server just sends
 *     the terminator.
 *     response: ( )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GetFileRevsCmd extends BaseCmd<GetFileRevsCmd.Params> {
  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final SvnServerWriter writer = context.getWriter();
    try {

      int startRev = getRevisionOrLatest(args.startRev, context);
      int endRev = getRevisionOrLatest(args.endRev, context);

      final boolean reverse = startRev > endRev;
      if (reverse) {
        final int tmp = startRev;
        startRev = endRev;
        endRev = tmp;
      }

      final String fullPath = context.getRepositoryPath(args.path);
      final GitRepository repository = context.getRepository();
      int rev = repository.getLastChange(fullPath, endRev);
      if (rev < 0) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, fullPath + " not found in revision " + endRev));
      }

      final GitFile head = repository.getRevisionInfo(rev).getFile(fullPath);
      if (head == null)
        throw new IllegalStateException();

      final List<GitFile> history = new ArrayList<>();
      walkFileHistory(repository, head, startRev, history::add);
      if (reverse)
        Collections.reverse(history);

      for (int index = history.size() - 1; index >= 0; --index) {
        final GitFile oldFile = index <= history.size() - 2 ? history.get(index + 1) : null;
        final GitFile newFile = history.get(index);

        final Map<String, String> propsDiff = DeltaCmd.getPropertiesDiff(oldFile, newFile);

        writer
            .listBegin()
            .string(newFile.getFullPath())
            .number(newFile.getRevision())
            .writeMap(newFile.getLastChange().getProperties(true))
            .writeMap(propsDiff)
            .bool(false) // TODO: issue #26. merged-revision
            .listEnd();

        try (InputStream prevStream = oldFile == null ? SVNFileUtil.DUMMY_IN : oldFile.openStream();
             InputStream newStream = newFile.openStream()) {
          new SVNDeltaGenerator().sendDelta(fullPath, prevStream, 0, newStream, new ISVNDeltaConsumer() {
            private boolean writeHeader = true;

            @Override
            public void applyTextDelta(String path, String baseChecksum) {
            }

            @Override
            public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
              try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                diffWindow.writeTo(stream, writeHeader, context.isCompressionEnabled());
                writeHeader = false;
                writer.binary(stream.toByteArray());
              } catch (IOException e) {
                throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR));
              }

              return null;
            }

            @Override
            public void textDeltaEnd(String path) throws SVNException {
              try {
                writer.binary(GitRepository.emptyBytes);
              } catch (IOException e) {
                throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR));
              }

            }
          }, false);
        }
      }
    } finally {
      // Yes, this is ugly. But otherwise, client hangs waiting forever.
      writer
          .word("done");
    }

    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
  }

  /**
   * TODO: This method is very similar to LogCmd#getLog. Maybe they can be combined?
   */
  private void walkFileHistory(@NotNull GitRepository repository, @NotNull GitFile start, int stopRev, @NotNull FileHistoryWalker walker) throws SVNException, IOException {
    @Nullable
    GitFile head = start;

    while (true) {
      walker.handle(head);

      VcsCopyFrom copyFrom = head.getCopyFrom();
      if (copyFrom == null) {
        final int prevRev = repository.getLastChange(head.getFullPath(), head.getRevision() - 1);
        if (prevRev >= 0) {
          // Same path, earlier commit
          copyFrom = new VcsCopyFrom(prevRev, head.getFullPath());
        }
      }

      // If null, it is the first revision where file was created
      if (copyFrom == null)
        break;

      if (copyFrom.getRevision() < stopRev)
        break;

      final GitRevision prevRevision = repository.getRevisionInfo(copyFrom.getRevision());
      final GitFile file = prevRevision.getFile(copyFrom.getPath());
      if (file == null)
        throw new IllegalStateException();
      head = file;
    }
  }

  interface FileHistoryWalker {
    void handle(@NotNull GitFile file) throws SVNException, IOException;
  }

  public static class Params {
    @NotNull
    private final String path;
    @NotNull
    private final int[] startRev;
    @NotNull
    private final int[] endRev;
    /**
     * TODO: issue #26.
     */
    private final boolean includeMergedRevisions;

    public Params(@NotNull String path, @NotNull int[] startRev, @NotNull int[] endRev, boolean includeMergedRevisions) {
      this.path = path;
      this.startRev = startRev;
      this.endRev = endRev;
      this.includeMergedRevisions = includeMergedRevisions;
    }
  }
}
