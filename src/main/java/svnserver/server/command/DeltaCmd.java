/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import svnserver.StringHelper;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.repository.Depth;
import svnserver.repository.SvnForbiddenException;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.git.GitFile;
import svnserver.server.SessionContext;
import svnserver.server.step.CheckPermissionStep;

import java.io.*;
import java.util.*;

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
public final class DeltaCmd extends BaseCmd<DeltaParams> {

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(DeltaCmd.class);
  @NotNull
  private final Class<? extends DeltaParams> arguments;

  public DeltaCmd(@NotNull Class<? extends DeltaParams> arguments) {
    this.arguments = arguments;
  }

  @NotNull
  static Map<String, String> getPropertiesDiff(@Nullable GitFile oldFile, @Nullable GitFile newFile) throws IOException, SVNException {
    final Map<String, String> result = new TreeMap<>();
    final Map<String, String> oldProps = oldFile != null ? oldFile.getProperties() : Collections.emptyMap();
    final Map<String, String> newProps = newFile != null ? newFile.getProperties() : Collections.emptyMap();

    for (Map.Entry<String, String> en : oldProps.entrySet()) {
      final String newProp = newProps.get(en.getKey());
      if (!en.getValue().equals(newProp))
        result.put(en.getKey(), newProp);
    }

    for (Map.Entry<String, String> en : newProps.entrySet()) {
      final String oldProp = oldProps.get(en.getKey());
      if (!en.getValue().equals(oldProp))
        result.put(en.getKey(), en.getValue());
    }

    return result;
  }

  @NotNull
  @Override
  public Class<? extends DeltaParams> getArguments() {
    return arguments;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull DeltaParams args) throws IOException, SVNException {
    log.debug("Enter report mode");
    ReportPipeline pipeline = new ReportPipeline(args);
    pipeline.reportCommand(context);
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
    private final Depth depth;

    public SetPathParams(@NotNull String path, int rev, boolean startEmpty, @NotNull String[] lockToken, @NotNull String depth) {
      this.path = path;
      this.rev = rev;
      this.startEmpty = startEmpty;
      this.lockToken = lockToken;
      this.depth = Depth.parse(depth);
    }

    @Override
    public String toString() {
      return "SetPathParams{" +
          "path='" + path + '\'' +
          ", rev=" + rev +
          ", startEmpty=" + startEmpty +
          ", lockToken=" + Arrays.toString(lockToken) +
          ", depth=" + depth +
          '}';
    }
  }

  private static class FailureInfo {
    private final int errorCode;
    private final String errorMessage;
    private final String errorFile;
    private final int errorLine;

    FailureInfo(@NotNull SvnServerParser parser) throws IOException {
      errorCode = parser.readNumber();
      errorMessage = parser.readText();
      errorFile = parser.readText();
      errorLine = parser.readNumber();
    }

    @Nullable
    public static FailureInfo read(@NotNull SvnServerParser parser) throws IOException {
      if (parser.readItem(ListBeginToken.class) == null) {
        return null;
      }
      final FailureInfo result = new FailureInfo(parser);
      parser.readToken(ListEndToken.class);
      return result;
    }

    public void write(@NotNull SvnServerWriter writer) throws IOException {
      writer.listBegin()
          .number(errorCode)
          .string(errorMessage)
          .string(errorFile)
          .number(errorLine)
          .listEnd();
    }
  }

  public static class ReportPipeline {
    @NotNull
    private final Map<String, BaseCmd<?>> commands;
    @NotNull
    private final DeltaParams params;
    @NotNull
    private final Map<String, Set<String>> forcedPaths = new HashMap<>();
    @NotNull
    private final Set<String> deletedPaths = new HashSet<>();
    @NotNull
    private final Map<String, SetPathParams> paths = new HashMap<>();
    @NotNull
    private final Deque<HeaderEntry> pathStack = new ArrayDeque<>();
    private int lastTokenId;

    public ReportPipeline(@NotNull DeltaParams params) {
      this.params = params;
      commands = new HashMap<>();
      commands.put("delete-path", new LambdaCmd<>(DeleteParams.class, this::deletePath));
      commands.put("set-path", new LambdaCmd<>(SetPathParams.class, this::setPathReport));
      commands.put("abort-report", new LambdaCmd<>(NoParams.class, this::abortReport));
      commands.put("finish-report", new LambdaCmd<>(NoParams.class, this::finishReport));
    }

    private SvnServerWriter getWriter(@NotNull SessionContext context) throws IOException, SVNException {
      for (HeaderEntry entry : pathStack) {
        entry.write();
      }
      return context.getWriter();
    }

    private void abortReport(@NotNull SessionContext context, @NotNull NoParams args) throws IOException, SVNException {
      final SvnServerWriter writer = getWriter(context);
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
    }

    private void finishReport(@NotNull SessionContext context, @NotNull NoParams args) {
      context.push(new CheckPermissionStep(this::complete, null));
    }

    public void setPathReport(@NotNull String path, int rev, boolean startEmpty, @NotNull SVNDepth depth) throws SVNException {
      final String wcPath = wcPath(path);
      paths.put(wcPath, new SetPathParams(path, rev, startEmpty, new String[0], depth.getName()));
      forcePath(wcPath);
    }

    @NotNull
    private String wcPath(@NotNull String name) {
      return joinPath(params.getPath(), name);
    }

    private void forcePath(@NotNull String wcPath) {
      String path = wcPath;
      while (!path.isEmpty()) {
        final String parent = StringHelper.parentDir(path);
        final Set<String> items = forcedPaths.computeIfAbsent(parent, s -> new HashSet<>());
        if (!items.add(path)) {
          break;
        }
        path = parent;
      }
    }

    @NotNull
    private String joinPath(@NotNull String prefix, @NotNull String name) {
      if (name.isEmpty()) return prefix;
      return prefix.isEmpty() ? name : (prefix + "/" + name);
    }

    private void setPathReport(@NotNull SessionContext context, @NotNull SetPathParams args) throws SVNException {
      context.push(this::reportCommand);
      final String wcPath = wcPath(args.path);
      paths.put(wcPath, args);
      forcePath(wcPath);
    }

    private void deletePath(@NotNull SessionContext context, @NotNull DeleteParams args) throws SVNException {
      context.push(this::reportCommand);
      final String wcPath = wcPath(args.path);
      forcePath(wcPath);
      deletedPaths.add(wcPath);
    }

    private void complete(@NotNull SessionContext context) throws IOException, SVNException {
      sendResponse(context, params.getPath(), params.getRev(context));
    }

    protected void sendDelta(@NotNull SessionContext context, @NotNull String path, int rev) throws IOException, SVNException {
      final SetPathParams rootParams = paths.get(wcPath(""));
      if (rootParams == null)
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA));

      final SvnServerWriter writer = getWriter(context);
      writer
          .listBegin()
          .word("target-rev")
          .listBegin().number(rev).listEnd()
          .listEnd();

      final String tokenId = createTokenId();

      final int rootRev = rootParams.rev;
      writer
          .listBegin()
          .word("open-root")
          .listBegin()
          .listBegin()
          .number(rootRev)
          .listEnd()
          .string(tokenId)
          .listEnd()
          .listEnd();
      final String fullPath = context.getRepositoryPath(path);

      final SVNURL targetPath = params.getTargetPath();
      final GitFile newFile;
      if (targetPath == null)
        newFile = context.getFile(rev, fullPath);
      else
        newFile = context.getFile(rev, targetPath);

      final GitFile oldFile = getPrevFile(context, path, context.getFile(rootRev, fullPath));
      updateEntry(context, path, oldFile, newFile, tokenId, path.isEmpty(), rootParams.depth, params.getDepth());
      writer
          .listBegin()
          .word("close-dir")
          .listBegin().string(tokenId).listEnd()
          .listEnd();
    }

    protected void sendResponse(@NotNull SessionContext context, @NotNull String path, int rev) throws IOException, SVNException {
      final SvnServerWriter writer = getWriter(context);
      sendDelta(context, path, rev);
      writer
          .listBegin()
          .word("close-edit")
          .listBegin().listEnd()
          .listEnd();
      final SvnServerParser parser = context.getParser();
      parser.readToken(ListBeginToken.class);

      final String clientStatus = parser.readText();
      switch (clientStatus) {
        case "failure": {
          parser.readToken(ListBeginToken.class);
          final List<FailureInfo> failures = new ArrayList<>();
          while (true) {
            final FailureInfo failure = FailureInfo.read(parser);
            if (failure == null) {
              break;
            }
            if (failure.errorFile.isEmpty()) {
              log.warn("Received client error: {} {}", failure.errorCode, failure.errorMessage);
            } else {
              log.warn("Received client error [{}:{}]: {} {}", failure.errorFile, failure.errorLine, failure.errorCode, failure.errorMessage);
            }
            failures.add(failure);
          }
          parser.readToken(ListEndToken.class);
          writer
              .listBegin()
              .word("abort-edit")
              .listBegin().listEnd()
              .listEnd();
          writer
              .listBegin()
              .word("failure")
              .listBegin();
          for (FailureInfo failure : failures) {
            failure.write(writer);
          }
          writer
              .listEnd()
              .listEnd();
          writer
              .listBegin();
          break;
        }
        case "success": {
          parser.skipItems();
          writer
              .listBegin()
              .word("success")
              .listBegin().listEnd()
              .listEnd();
          break;
        }
        default: {
          log.error("Unexpected client status: {}", clientStatus);
          throw new EOFException("Unexpected client status");
        }
      }
    }

    private String createTokenId() {
      return "t" + ++lastTokenId;
    }

    private void updateDir(@NotNull SessionContext context,
                           @NotNull String wcPath,
                           @Nullable GitFile prevFile,
                           @NotNull GitFile newFile,
                           @NotNull String parentTokenId,
                           boolean rootDir,
                           @NotNull Depth wcDepth,
                           @NotNull Depth requestedDepth) throws IOException, SVNException {
      final String tokenId;
      final HeaderEntry header;
      GitFile oldFile;
      try {
        newFile.getEntries();
      } catch (SvnForbiddenException ignored) {
        getWriter(context)
            .listBegin()
            .word("absent-dir")
            .listBegin()
            .string(newFile.getFileName())
            .string(parentTokenId)
            .listEnd()
            .listEnd();
        return;
      }
      if (rootDir && wcPath.isEmpty()) {
        tokenId = parentTokenId;
        oldFile = prevFile;
        header = null;
      } else {
        tokenId = createTokenId();
        header = sendEntryHeader(context, wcPath, prevFile, newFile, "dir", parentTokenId, tokenId, writer -> writer
            .listBegin()
            .word("close-dir")
            .listBegin().string(tokenId).listEnd()
            .listEnd());
        oldFile = header.file;
      }
      if (getStartEmpty(wcPath)) {
        oldFile = null;
      }

      if (rootDir) {
        sendRevProps(getWriter(context), newFile, "dir", tokenId);
      }
      updateProps(context, "dir", tokenId, oldFile, newFile);
      updateDirEntries(context, wcPath, oldFile, newFile, tokenId, wcDepth, requestedDepth);

      if (header != null) {
        header.close();
      }
    }

    private void updateDirEntries(@NotNull SessionContext context,
                                  @NotNull String wcPath,
                                  @Nullable GitFile oldFile,
                                  @NotNull GitFile newFile,
                                  @NotNull String tokenId,
                                  @NotNull Depth wcDepth,
                                  @NotNull Depth requestedDepth) throws IOException, SVNException {
      final Depth.Action dirAction = wcDepth.determineAction(requestedDepth, true);
      final Depth.Action fileAction = wcDepth.determineAction(requestedDepth, false);

      final Map<String, GitFile> newEntries = new TreeMap<>();
      for (GitFile entry : newFile.getEntries()) {
        newEntries.put(entry.getFileName(), entry);
      }

      final Set<String> forced = new HashSet<>(forcedPaths.getOrDefault(wcPath, Collections.emptySet()));
      final Map<String, GitFile> oldEntries;
      if (oldFile != null) {
        oldEntries = new TreeMap<>();
        for (GitFile oldEntry : oldFile.getEntries()) {
          final String entryPath = joinPath(wcPath, oldEntry.getFileName());
          if (newEntries.containsKey(oldEntry.getFileName())) {
            oldEntries.put(oldEntry.getFileName(), oldEntry);
            continue;
          }
          removeEntry(context, entryPath, oldEntry.getLastChange().getId(), tokenId);
          forced.remove(entryPath);
        }
      } else {
        oldEntries = Collections.emptyMap();
      }

      for (String entryPath : forced) {
        String entryName = StringHelper.getChildPath(wcPath, entryPath);
        if ((entryName != null) && newEntries.containsKey(entryName)) {
          continue;
        }
        removeEntry(context, entryPath, newFile.getLastChange().getId(), tokenId);
      }

      for (GitFile newEntry : newFile.getEntries()) {
        final String entryPath = joinPath(wcPath, newEntry.getFileName());
        final GitFile oldEntry = getPrevFile(context, entryPath, oldEntries.get(newEntry.getFileName()));

        final Depth.Action action = newEntry.isDirectory() ? dirAction : fileAction;

        if (!forced.remove(entryPath) && newEntry.equals(oldEntry) && action == Depth.Action.Normal && requestedDepth == wcDepth)
          // Same entry with same depth parameter.
          continue;

        if (action == Depth.Action.Skip)
          continue;

        final Depth entryDepth = getWcDepth(entryPath, wcDepth);
        updateEntry(context, entryPath, action == Depth.Action.Upgrade ? null : oldEntry, newEntry, tokenId, false, entryDepth, requestedDepth.deepen());
      }
    }

    private void updateProps(@NotNull SessionContext context, @NotNull String type, @NotNull String tokenId, @Nullable GitFile oldFile, @NotNull GitFile newFile) throws IOException, SVNException {
      final Map<String, String> propsDiff = getPropertiesDiff(oldFile, newFile);
      if (oldFile == null)
        getWriter(context);

      for (Map.Entry<String, String> entry : propsDiff.entrySet())
        changeProp(getWriter(context), type, tokenId, entry.getKey(), entry.getValue());
    }

    private void updateFile(@NotNull SessionContext context, @NotNull String wcPath, @Nullable GitFile prevFile, @NotNull GitFile newFile, @NotNull String parentTokenId) throws IOException, SVNException {
      final String tokenId = createTokenId();
      final String md5 = newFile.getMd5();
      try (final HeaderEntry header = sendEntryHeader(context, wcPath, prevFile, newFile, "file", parentTokenId, tokenId, writer -> writer
          .listBegin()
          .word("close-file")
          .listBegin()
          .string(tokenId)
          .listBegin()
          .string(md5)
          .listEnd()
          .listEnd()
          .listEnd())) {
        final GitFile oldFile = header.file;
        if (oldFile == null || !newFile.getContentHash().equals(oldFile.getContentHash())) {
          final SvnServerWriter writer = getWriter(context);
          writer
              .listBegin()
              .word("apply-textdelta")
              .listBegin()
              .string(tokenId)
              .listBegin()
              .listEnd()
              .listEnd()
              .listEnd();

          if (params.needDeltas()) {
            final SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
            try (InputStream source = openStream(oldFile);
                 InputStream target = newFile.openStream()) {
              final boolean compress = context.isCompressionEnabled();
              final String validateMd5 = deltaGenerator.sendDelta(newFile.getFileName(), source, 0, target, new ISVNDeltaConsumer() {
                private boolean header = true;

                @Override
                public void applyTextDelta(String path, String baseChecksum) {
                }

                @Override
                public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
                  try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                    diffWindow.writeTo(stream, header, compress);
                    header = false;
                    writer
                        .listBegin()
                        .word("textdelta-chunk")
                        .listBegin()
                        .string(tokenId)
                        .binary(stream.toByteArray())
                        .listEnd()
                        .listEnd();
                    return null;
                  } catch (IOException e) {
                    throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR), e);
                  }
                }

                @Override
                public void textDeltaEnd(String path) {
                }
              }, true);
              if (!validateMd5.equals(md5)) {
                throw new IllegalStateException("MD5 checksum mismatch: some shit happends.");
              }
            }
          }
          writer
              .listBegin()
              .word("textdelta-end")
              .listBegin()
              .string(tokenId)
              .listEnd()
              .listEnd();
        }
        updateProps(context, "file", tokenId, oldFile, newFile);
      }
    }

    @NotNull
    private InputStream openStream(@Nullable GitFile file) throws IOException, SVNException {
      return file == null ? new ByteArrayInputStream(new byte[0]) : file.openStream();
    }

    @NotNull
    private Depth getWcDepth(@NotNull String wcPath, @NotNull Depth parentWcDepth) {
      final SetPathParams params = paths.get(wcPath);
      if (params == null)
        return parentWcDepth.deepen();

      return params.depth;
    }

    private boolean getStartEmpty(@NotNull String wcPath) {
      final SetPathParams params = paths.get(wcPath);
      return params != null && params.startEmpty;
    }

    @Nullable
    private GitFile getPrevFile(@NotNull SessionContext context, @NotNull String wcPath, @Nullable GitFile oldFile) throws IOException, SVNException {
      if (deletedPaths.contains(wcPath))
        return null;

      final SetPathParams pathParams = paths.get(wcPath);
      if (pathParams == null)
        return oldFile;

      if (pathParams.rev == 0)
        return null;

      return context.getFile(pathParams.rev, wcPath);
    }

    private void updateEntry(@NotNull SessionContext context,
                             @NotNull String wcPath,
                             @Nullable GitFile oldFile,
                             @Nullable GitFile newFile,
                             @NotNull String parentTokenId,
                             boolean rootDir,
                             @NotNull Depth wcDepth,
                             @NotNull Depth requestedDepth) throws IOException, SVNException {
      if (oldFile != null)
        if (newFile == null || !oldFile.getKind().equals(newFile.getKind()))
          removeEntry(context, wcPath, oldFile.getLastChange().getId(), parentTokenId);

      if (newFile == null)
        return;

      if (newFile.isDirectory())
        updateDir(context, wcPath, oldFile, newFile, parentTokenId, rootDir, wcDepth, requestedDepth);
      else {
        try {
          updateFile(context, wcPath, oldFile, newFile, parentTokenId);
        } catch (SvnForbiddenException ignored) {
          getWriter(context)
              .listBegin()
              .word("absent-file")
              .listBegin()
              .string(newFile.getFileName())
              .string(parentTokenId)
              .listEnd()
              .listEnd();
        }
      }
    }

    private void removeEntry(@NotNull SessionContext context, @NotNull String wcPath, int rev, @NotNull String parentTokenId) throws IOException, SVNException {
      if (deletedPaths.contains(wcPath)) {
        return;
      }
      getWriter(context)
          .listBegin()
          .word("delete-entry")
          .listBegin()
          .string(wcPath)
          .listBegin()
          .number(rev)
          .listEnd()
          .string(parentTokenId)
          .listEnd()
          .listEnd();
    }

    private void sendOpenEntry(@NotNull SvnServerWriter writer, @NotNull String command, @NotNull String fileName, @NotNull String parentTokenId, @NotNull String tokenId, @Nullable Integer revision) throws IOException {
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

    @NotNull
    private HeaderEntry sendEntryHeader(@NotNull SessionContext context, @NotNull String wcPath, @Nullable GitFile oldFile, @NotNull GitFile newFile, @NotNull String type, @NotNull String parentTokenId, @NotNull String tokenId, @NotNull HeaderWriter endWriter) throws IOException, SVNException {
      if (oldFile == null) {
        final VcsCopyFrom copyFrom = params.getSendCopyFrom().getCopyFrom(wcPath(""), newFile);
        final GitFile entryFile = copyFrom != null ? context.getRepository().getRevisionInfo(copyFrom.getRevision()).getFile(copyFrom.getPath()) : null;
        final HeaderEntry entry = new HeaderEntry(context, entryFile, writer -> {
          sendNewEntry(writer, "add-" + type, wcPath, parentTokenId, tokenId, copyFrom);
          sendRevProps(writer, newFile, type, tokenId);
        }, endWriter, pathStack);
        getWriter(context);
        return entry;
      } else {
        return new HeaderEntry(context, oldFile, writer -> {
          sendOpenEntry(writer, "open-" + type, wcPath, parentTokenId, tokenId, oldFile.getLastChange().getId());
          sendRevProps(writer, newFile, type, tokenId);
        }, endWriter, pathStack);
      }
    }

    private void sendRevProps(@NotNull SvnServerWriter writer, @NotNull GitFile newFile, @NotNull String type, @NotNull String tokenId) throws IOException {
      if (params.isIncludeInternalProps()) {
        for (Map.Entry<String, String> prop : newFile.getRevProperties().entrySet()) {
          changeProp(writer, type, tokenId, prop.getKey(), prop.getValue());
        }
      }
    }

    private void sendNewEntry(@NotNull SvnServerWriter writer, @NotNull String command, @NotNull String fileName, @NotNull String parentTokenId, @NotNull String tokenId, @Nullable VcsCopyFrom copyFrom) throws IOException {
      writer
          .listBegin()
          .word(command)
          .listBegin()
          .string(fileName)
          .string(parentTokenId)
          .string(tokenId)
          .listBegin();
      if (copyFrom != null) {
        writer.string(copyFrom.getPath());
        writer.number(copyFrom.getRevision());
      }
      writer
          .listEnd()
          .listEnd()
          .listEnd();
    }

    private void changeProp(@NotNull SvnServerWriter writer, @NotNull String type, @NotNull String tokenId, @NotNull String key, @Nullable String value) throws IOException {
      writer
          .listBegin()
          .word("change-" + type + "-prop")
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

    @SuppressWarnings("unchecked")
    private void reportCommand(@NotNull SessionContext context) throws IOException, SVNException {
      final SvnServerParser parser = context.getParser();
      final SvnServerWriter writer = context.getWriter();
      parser.readToken(ListBeginToken.class);
      final String cmd = parser.readText();
      log.debug("Report command: {}", cmd);
      final BaseCmd command = commands.get(cmd);
      if (command != null) {
        Object param = MessageParser.parse(command.getArguments(), parser);
        parser.readToken(ListEndToken.class);
        command.process(context, param);
      } else {
        log.error("Unsupported command: {}", cmd);
        BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD, "Unsupported command: " + cmd));
        parser.skipItems();
      }
    }

    @FunctionalInterface
    private interface HeaderWriter {
      void write(@NotNull SvnServerWriter writer) throws IOException, SVNException;
    }

    private static class HeaderEntry implements AutoCloseable {

      @NotNull
      private final SessionContext context;
      @Nullable
      private final GitFile file;
      @NotNull
      private final HeaderWriter beginWriter;
      @NotNull
      private final HeaderWriter endWriter;
      private final Deque<HeaderEntry> pathStack;
      private boolean writed = false;

      private HeaderEntry(@NotNull SessionContext context, @Nullable GitFile file, @NotNull HeaderWriter beginWriter, @NotNull HeaderWriter endWriter, @NotNull Deque<HeaderEntry> pathStack) {
        this.context = context;
        this.file = file;
        this.beginWriter = beginWriter;
        this.endWriter = endWriter;
        this.pathStack = pathStack;
        pathStack.addLast(this);
      }

      public void write() throws IOException, SVNException {
        if (!writed) {
          writed = true;
          beginWriter.write(context.getWriter());
        }
      }

      @Override
      public void close() throws IOException, SVNException {
        if (writed) {
          endWriter.write(context.getWriter());
        }
        pathStack.removeLast();
      }
    }
  }
}
