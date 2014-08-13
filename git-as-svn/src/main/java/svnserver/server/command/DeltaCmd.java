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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

  public static class DeleteParams {
    @NotNull
    private final String path;

    public DeleteParams(@NotNull String path) {
      this.path = path;
    }
  }

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
  @NotNull
  private static final String[] emptyStrings = new String[0];

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
    @NotNull
    private final Set<String> deletedPaths = new HashSet<>();
    @NotNull
    private final Map<String, SetPathParams> paths = new HashMap<>();

    public ReportPipeline(@NotNull DeltaParams params) {
      this.params = params;
      paths.put("", new SetPathParams("", 0, true, emptyStrings, ""));
      commands = new HashMap<>();
      commands.put("delete-path", new LambdaCmd<>(DeleteParams.class, this::deletePath));
      commands.put("abort-report", new LambdaCmd<>(NoParams.class, this::abortReport));
      commands.put("set-path", new LambdaCmd<>(SetPathParams.class, this::setPathReport));
      commands.put("finish-report", new LambdaCmd<>(NoParams.class, this::finishReport));
    }

    private void finishReport(@NotNull SessionContext context, @NotNull NoParams args) {
      context.push(new CheckPermissionStep(this::complete));
    }

    private void setPathReport(@NotNull SessionContext context, @NotNull SetPathParams args) {
      context.push(this::reportCommand);
      paths.put(joinPath(params.getPath(), args.path), args);
    }

    private void deletePath(@NotNull SessionContext context, @NotNull DeleteParams args) {
      context.push(this::reportCommand);
      deletedPaths.add(joinPath(params.getPath(), args.path));
    }

    private void complete(@NotNull SessionContext context) throws IOException, ClientErrorException {
      sendResponse(context, params.getPath(), params.getRev(context));
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
      final String tokenId = createTokenId();
      writer
          .listBegin()
          .word("open-root")
          .listBegin()
          .listBegin()
          .number(rev)
          .listEnd()
          .string(tokenId)
          .listEnd()
          .listEnd();
      FileInfo file = context.getRepository().getRevisionInfo(rev).getFile(context.getRepositoryPath(path));
      updateEntry(context, path, getPrevFile(context, path, file), file, tokenId, path.isEmpty());
      writer
          .listBegin()
          .word("close-dir")
          .listBegin().string(tokenId).listEnd()
          .listEnd();
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
    private void updateDir(@NotNull SessionContext context, @NotNull String fullPath, @Nullable FileInfo oldFile, @NotNull FileInfo newFile, @NotNull String parentTokenId, boolean rootDir) throws IOException, ClientErrorException {
      final SvnServerWriter writer = context.getWriter();
      final String tokenId;
      if (rootDir) {
        tokenId = parentTokenId;
      } else {
        tokenId = createTokenId();
        if (oldFile == null) {
          sendStartEntry(writer, "add-dir", fullPath, parentTokenId, tokenId, null);
        } else {
          sendStartEntry(writer, "open-dir", fullPath, parentTokenId, tokenId, newFile.getLastChange().getId());
        }
      }
      updateProps(writer, "change-dir-prop", tokenId, oldFile, newFile);
      final Map<String, FileInfo> oldEntries = new HashMap<>();
      if (oldFile != null) {
        for (FileInfo entry : oldFile.getEntries()) {
          oldEntries.put(entry.getFileName(), entry);
        }
      }
      for (FileInfo newEntry : newFile.getEntries()) {
        FileInfo oldEntry = getPrevFile(context, joinPath(fullPath, newEntry.getFileName()), oldEntries.remove(newEntry.getFileName()));
        if (newEntry.equals(oldEntry)) {
          // Same entry.
          continue;
        }
        updateEntry(context, joinPath(fullPath, newEntry.getFileName()), oldEntry, newEntry, tokenId, false);
      }
      for (FileInfo entry : oldEntries.values()) {
        removeEntry(context, joinPath(fullPath, entry.getFileName()), entry.getLastChange().getId(), tokenId);
      }
      if (!rootDir) {
        writer
            .listBegin()
            .word("close-dir")
            .listBegin().string(tokenId).listEnd()
            .listEnd();
      }
    }

    private void updateProps(@NotNull SvnServerWriter writer, @NotNull String command, @NotNull String tokenId, @Nullable FileInfo oldFile, @NotNull FileInfo newFile) throws IOException {
      final Map<String, String> oldProps = oldFile != null ? oldFile.getProperties(true) : new HashMap<>();
      for (Map.Entry<String, String> entry : newFile.getProperties(true).entrySet()) {
        if (!entry.getValue().equals(oldProps.remove(entry.getKey()))) {
          changeProp(writer, command, tokenId, entry.getKey(), entry.getValue());
        }
      }
      for (String propName : oldProps.keySet()) {
        changeProp(writer, command, tokenId, propName, null);
      }
    }

    private void updateFile(@NotNull SessionContext context, @NotNull String fullPath, @Nullable FileInfo oldFile, @NotNull FileInfo newFile, @NotNull String parentTokenId) throws IOException, ClientErrorException {
      final SvnServerWriter writer = context.getWriter();
      final String tokenId = createTokenId();
      if (oldFile == null) {
        sendStartEntry(writer, "add-file", fullPath, parentTokenId, tokenId, null);
      } else {
        sendStartEntry(writer, "open-file", fullPath, parentTokenId, tokenId, newFile.getLastChange().getId());
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
      writer
          .listBegin()
          .word("close-file")
          .listBegin()
          .string(tokenId)
          .listBegin()
          .string(md5)
          .listEnd()
          .listEnd()
          .listEnd();
    }

    @NotNull
    private InputStream openStream(@Nullable FileInfo file) throws IOException {
      return file == null ? new ByteArrayInputStream(new byte[0]) : file.openStream();
    }

    @Nullable
    private FileInfo getPrevFile(@NotNull SessionContext context, @NotNull String fullPath, @Nullable FileInfo oldFile) throws IOException, ClientErrorException {
      if (deletedPaths.contains(fullPath)) {
        return null;
      }
      final SetPathParams pathParams = paths.get(fullPath);
      if (pathParams == null) {
        return oldFile;
      }
      if (pathParams.startEmpty) {
        return null;
      }
      String repositoryPath = context.getRepositoryPath(fullPath);
      log.warn("{} -> {} ({})", fullPath, repositoryPath, oldFile);
      return context.getRepository().getRevisionInfo(pathParams.rev).getFile(repositoryPath);
    }

    private void updateEntry(@NotNull SessionContext context, @NotNull String fullPath, @Nullable FileInfo oldFile, @Nullable FileInfo newFile, @NotNull String parentTokenId, boolean rootDir) throws IOException, ClientErrorException {
      if (newFile == null) {
        if (oldFile != null) {
          removeEntry(context, fullPath, oldFile.getLastChange().getId(), parentTokenId);
        }
      } else if ((oldFile != null) && (!newFile.getKind().equals(oldFile.getKind()))) {
        removeEntry(context, fullPath, oldFile.getLastChange().getId(), parentTokenId);
        updateEntry(context, fullPath, null, newFile, parentTokenId, rootDir);
      } else if (newFile.isDirectory()) {
        updateDir(context, fullPath, oldFile, newFile, parentTokenId, rootDir);
      } else {
        updateFile(context, fullPath, oldFile, newFile, parentTokenId);
      }
    }

    private void removeEntry(@NotNull SessionContext context, @NotNull String fullPath, int rev, @NotNull String parentTokenId) throws IOException {
      if (deletedPaths.contains(fullPath)) {
        return;
      }
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("delete-entry")
          .listBegin()
          .string(fullPath)
          .listBegin()
          .number(rev) // todo: ???
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

    private String joinPath(@NotNull String prefix, @NotNull String name) {
      if (name.isEmpty()) return prefix;
      return prefix.isEmpty() ? name : (prefix + "/" + name);
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
