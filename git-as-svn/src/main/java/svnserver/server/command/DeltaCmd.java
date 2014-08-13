package svnserver.server.command;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import svnserver.SvnConstants;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.repository.FileInfo;
import svnserver.server.SessionContext;
import svnserver.server.error.ClientErrorException;
import svnserver.server.step.CheckPermissionStep;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Delta commands.
 * <pre>
 * To reduce round-trip delays, report commands do not return responses.
 *    Any errors resulting from a report call will be returned to the client
 *    by the command which invoked the report (following an abort-edit
 *    call).  Errors resulting from an abort-report call are ignored.
 *
 *    set-path:
 *    params: ( path:string rev:number start-empty:bool
 *    ? [ lock-token:string ] ? depth:word )
 *
 *    delete-path:
 *    params: ( path:string )
 *
 *    link-path:
 *    params: ( path:string url:string rev:number start-empty:bool
 *    ? [ lock-token:string ] ? depth:word )
 *
 *    finish-report:
 *    params: ( )
 *
 *    abort-report
 *    params: ( )
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public abstract class DeltaCmd<T extends DeltaParams> extends BaseCmd<T> {

  public static class NoParams {
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class SetPathParams {
    @NotNull
    private final String path;
    private final int rev;
    private final boolean startEmpty;
    @NotNull
    private final String[] lockToken;
    @NotNull
    private final String depth;

    public SetPathParams(@NotNull String path, int rev, boolean startEmpty, @NotNull String[] lockToken, @NotNull String depth) {
      this.path = path;
      this.rev = rev;
      this.startEmpty = startEmpty;
      this.lockToken = lockToken;
      this.depth = depth;
    }
  }

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(DeltaCmd.class);

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull T args) throws IOException, ClientErrorException {
    log.info("Enter report mode");
    ReportPipeline pipeline = new ReportPipeline(args);
    pipeline.reportCommand(context);
  }

  public static class ReportPipeline {
    private int lastTokenId;
    @NotNull
    private final Map<String, BaseCmd<?>> commands;
    @NotNull
    private final DeltaParams params;

    public ReportPipeline(@NotNull DeltaParams params) {
      this.params = params;
      commands = new HashMap<>();
      commands.put("abort-report", new LambdaCmd<>(NoParams.class, this::abortReport));
      commands.put("set-path", new LambdaCmd<>(SetPathParams.class, this::setPathReport));
      commands.put("finish-report", new LambdaCmd<>(NoParams.class, this::finishReport));
    }

    private void finishReport(@NotNull SessionContext context, @NotNull NoParams args) {
      context.push(new CheckPermissionStep(this::complete));
    }

    private void setPathReport(@NotNull SessionContext context, @NotNull SetPathParams args) {
      context.push(this::reportCommand);
    }

    private void complete(@NotNull SessionContext context) throws IOException, ClientErrorException {
      sendResponse(context, params.getPath(context), params.getRev(context));
    }

    protected void sendResponse(@NotNull SessionContext context, @NotNull String path, int rev) throws IOException, ClientErrorException {
      //contents = repo.get_files(url, rev)
      //updateDir("", rev, path, contents)
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("target-rev")
          .listBegin().number(rev).listEnd()
          .listEnd();
      FileInfo file = context.getRepository().getRevisionInfo(rev).getFile(path);
      updateDir(context, null, file, null);
      writer
          .listBegin()
          .word("close-edit")
          .listBegin().listEnd()
          .listEnd();
      final SvnServerParser parser = context.getParser();
      parser.readToken(ListBeginToken.class);
      if (!"success".equals(parser.readText())) {
        parser.skipItems();
        // self.link.send_msg(gen.tuple('abort-edit'))
        // self.link.send_msg(gen.error(errno, errmsg))
      } else {
        parser.skipItems();
        writer
            .listBegin()
            .word("success")
            .listBegin().listEnd()
            .listEnd();
      }
    }

    private String createTokenId() {
      return "t" + String.valueOf(++lastTokenId);
    }

    //  private void updateDir(@NotNull String path,int rev, want, entry, Object parentToken=None) throws IOException {
    private void updateDir(@NotNull SessionContext context, @Nullable FileInfo oldFile, @NotNull FileInfo newFile, @Nullable String parentTokenId) throws IOException, ClientErrorException {
      final SvnServerWriter writer = context.getWriter();
      final String tokenId = createTokenId();
      if (parentTokenId == null) {
        writer
            .listBegin()
            .word("open-root")
            .listBegin()
            .listBegin()
            .number(newFile.getLastChange().getId())
            .listEnd()
            .string(tokenId)
            .listEnd()
            .listEnd();
      } else if (oldFile == null) {
        sendStartEntry(writer, "add-dir", newFile.getFileName(), parentTokenId, tokenId, null);
      } else {
        sendStartEntry(writer, "open-dir", newFile.getFileName(), parentTokenId, tokenId, newFile.getLastChange().getId());
      }
      final Map<String, FileInfo> newEntries = new HashMap<>();
      for (FileInfo entry : newFile.getEntries()) {
        newEntries.put(entry.getFileName(), entry);
      }
      if (oldFile != null) {
        for (FileInfo entry : oldFile.getEntries()) {
          FileInfo newEntry = newEntries.remove(entry.getFileName());
          if (entry.equals(newEntry)) {
            // Same entry.
            continue;
          }
          if (newEntry == null) {
            removeEntry(context, entry, tokenId);
          } else if (!newEntry.getKind().equals(entry.getKind())) {
            removeEntry(context, entry, tokenId);
            updateEntry(context, null, newEntry, tokenId);
          } else {
            updateEntry(context, entry, newEntry, tokenId);
          }
        }
      }
      for (FileInfo entry : newEntries.values()) {
        updateEntry(context, null, entry, tokenId);
      }
      updateProps(writer, "change-dir-prop", tokenId, oldFile, newFile);
      writer
          .listBegin()
          .word("close-dir")
          .listBegin().string(tokenId).listEnd()
          .listEnd();
    }

    private void updateProps(@NotNull SvnServerWriter writer, @NotNull String command, @NotNull String tokenId, @Nullable FileInfo oldFile, @NotNull FileInfo newFile) throws IOException {
      final Map<String, String> properties = newFile.getProperties(true);
      for (Map.Entry<String, String> entry : properties.entrySet()) {
        changeProp(writer, command, tokenId, entry.getKey(), entry.getValue());
      }
      if (oldFile != null) {
        for (String propName : oldFile.getProperties(true).keySet()) {
          if (!properties.containsKey(propName)) {
            changeProp(writer, command, tokenId, propName, null);
          }
        }
      }
    }

    private void updateFile(@NotNull SessionContext context, @Nullable FileInfo oldFile, @NotNull FileInfo newFile, @NotNull String parentTokenId) throws IOException, ClientErrorException {
      final SvnServerWriter writer = context.getWriter();
      final String tokenId = createTokenId();
      if (oldFile == null) {
        sendStartEntry(writer, "add-file", newFile.getFileName(), parentTokenId, tokenId, null);
      } else {
        sendStartEntry(writer, "open-file", newFile.getFileName(), parentTokenId, tokenId, newFile.getLastChange().getId());
      }
      writer
          .listBegin()
          .word("apply-textdelta")
          .listBegin()
          .string(tokenId)
          .listBegin()
          .listEnd()
          .listEnd()
          .listEnd();

      SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
      final String md5;
      try (InputStream source = openStream(oldFile);
           InputStream target = newFile.openStream()) {
        final boolean compress = context.hasCapability("svndiff1");
        md5 = deltaGenerator.sendDelta(newFile.getFileName(), source, 0, target, new ISVNDeltaConsumer() {
          private boolean header = true;

          @Override
          public void applyTextDelta(String path, String baseChecksum) throws SVNException {

          }

          @Override
          public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            try (ByteOutputStream stream = new ByteOutputStream()) {
              diffWindow.writeTo(stream, header, compress);
              header = false;
              writer
                  .listBegin()
                  .word("textdelta-chunk")
                  .listBegin()
                  .string(tokenId)
                  .binary(stream.getBytes(), 0, stream.getCount())
                  .listEnd()
                  .listEnd();
              return null;
            } catch (IOException e) {
              throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE, e);
            }
          }

          @Override
          public void textDeltaEnd(String path) throws SVNException {
          }
        }, true);
      } catch (SVNException e) {
        throw new ClientErrorException(e.getErrorMessage().getErrorCode().getCode(), e.getMessage());
      }
      writer
          .listBegin()
          .word("textdelta-end")
          .listBegin()
          .string(tokenId)
          .listEnd()
          .listEnd();
      updateProps(writer, "change-file-prop", tokenId, oldFile, newFile);
      // todo:
      writer
          .listBegin()
          .word("close-file")
          .listBegin()
          .string(tokenId)
          .listBegin()
          .string(newFile.getMd5())
          .listEnd()
          .listEnd()
          .listEnd();
      if (newFile.getMd5().equals(md5)) {
        // todo:
      }
    }

    @NotNull
    private InputStream openStream(@Nullable FileInfo file) throws IOException {
      return file == null ? new ByteArrayInputStream(new byte[0]) : file.openStream();
    }

    private void updateEntry(@NotNull SessionContext context, @Nullable FileInfo oldFile, @NotNull FileInfo newFile, @NotNull String parentTokenId) throws IOException, ClientErrorException {
      if (newFile.isDirectory()) {
        updateDir(context, oldFile, newFile, parentTokenId);
      } else {
        updateFile(context, oldFile, newFile, parentTokenId);
      }
    }

    private void removeEntry(@NotNull SessionContext context, @NotNull FileInfo entry, @NotNull String parentTokenId) throws IOException {
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("delete-entry")
          .listBegin()
          .string(entry.getFileName())
          .listBegin()
          .number(entry.getLastChange().getId()) // todo: ???
          .listEnd()
          .string(parentTokenId)
          .listEnd()
          .listEnd();
    }

    private void sendStartEntry(@NotNull SvnServerWriter writer, @NotNull String command, @NotNull String fileName, @NotNull String parentTokenId, @NotNull String tokenId, @Nullable Integer revision) throws IOException {
      writer
          .listBegin()
          .word(command)
          .listBegin()
          .string(fileName)
          .string(parentTokenId)
          .string(tokenId)
          .listBegin();
      if (revision != null) {
        writer.number(revision);
      }
      writer
          .listEnd()
          .listEnd()
          .listEnd();
    }

    private void changeProp(@NotNull SvnServerWriter writer, @NotNull String command, @NotNull String tokenId, @NotNull String key, @Nullable String value) throws IOException {
      writer
          .listBegin()
          .word(command)
          .listBegin()
          .string(tokenId)
          .string(key)
          .listBegin();
      if (value != null) {
        writer
            .string(value);
      }
      writer
          .listEnd()
          .listEnd()
          .listEnd();
    }

    private void abortReport(@NotNull SessionContext context, @NotNull NoParams args) throws ClientErrorException {
      throw new ClientErrorException(SvnConstants.ERROR_UNIMPLEMENTED, "Unsupported report command: finish-report");
    }

    private void reportCommand(@NotNull SessionContext context) throws IOException, ClientErrorException {
      final SvnServerParser parser = context.getParser();
      final SvnServerWriter writer = context.getWriter();
      parser.readToken(ListBeginToken.class);
      final String cmd = parser.readText();
      log.info("Report command: {}", cmd);
      final BaseCmd command = commands.get(cmd);
      if (command != null) {
        Object param = MessageParser.parse(command.getArguments(), parser);
        parser.readToken(ListEndToken.class);
        //noinspection unchecked
        command.process(context, param);
      } else {
        BaseCmd.sendError(writer, SvnConstants.ERROR_UNIMPLEMENTED, "Unsupported command: " + cmd);
        parser.skipItems();
      }
    }
  }
}
