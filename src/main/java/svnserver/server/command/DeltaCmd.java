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
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsFile;
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
  private final Class<? extends DeltaParams> arguments;

  public DeltaCmd(@NotNull Class<? extends DeltaParams> arguments) {
    this.arguments = arguments;
  }

  @NotNull
  @Override
  public Class<? extends DeltaParams> getArguments() {
    return arguments;
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

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(DeltaCmd.class);

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull DeltaParams args) throws IOException, SVNException {
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
    private final Map<String, Set<String>> forcedPaths = new HashMap<>();
    @NotNull
    private final Set<String> deletedPaths = new HashSet<>();
    @NotNull
    private final Map<String, SetPathParams> paths = new HashMap<>();

    public ReportPipeline(@NotNull DeltaParams params) {
      this.params = params;
      commands = new HashMap<>();
      commands.put("delete-path", new LambdaCmd<>(DeleteParams.class, this::deletePath));
      commands.put("set-path", new LambdaCmd<>(SetPathParams.class, this::setPathReport));
      commands.put("abort-report", new LambdaCmd<>(NoParams.class, this::abortReport));
      commands.put("finish-report", new LambdaCmd<>(NoParams.class, this::finishReport));
    }

    private void abortReport(@NotNull SessionContext context, @NotNull NoParams args) throws IOException {
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
    }

    private void finishReport(@NotNull SessionContext context, @NotNull NoParams args) {
      context.push(new CheckPermissionStep(this::complete));
    }

    public void setPathReport(@NotNull String path, int rev, boolean startEmpty, @NotNull SVNDepth depth) throws SVNException {
      final String wcPath = wcPath(path);
      paths.put(wcPath, new SetPathParams(path, rev, startEmpty, new String[0], depth.getName()));
      forcePath(wcPath);
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

    private void complete(@NotNull SessionContext context) throws IOException, SVNException {
      sendResponse(context, params.getPath(), params.getRev(context));
    }

    protected void sendDelta(@NotNull SessionContext context, @NotNull String path, int rev) throws IOException, SVNException {
      final SetPathParams rootParams = paths.get(wcPath(""));
      if (rootParams == null)
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA));

      final SvnServerWriter writer = context.getWriter();
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
      final VcsFile newFile;
      if (targetPath == null)
        newFile = context.getFile(rev, fullPath);
      else
        newFile = context.getFile(rev, targetPath);

      final VcsFile oldFile = getPrevFile(context, path, context.getFile(rootRev, fullPath));
      updateEntry(context, path, oldFile, newFile, tokenId, path.isEmpty(), rootParams.depth, params.getDepth());
      writer
          .listBegin()
          .word("close-dir")
          .listBegin().string(tokenId).listEnd()
          .listEnd();
    }

    protected void sendResponse(@NotNull SessionContext context, @NotNull String path, int rev) throws IOException, SVNException {
      final SvnServerWriter writer = context.getWriter();
      sendDelta(context, path, rev);
      writer
          .listBegin()
          .word("close-edit")
          .listBegin().listEnd()
          .listEnd();
      final SvnServerParser parser = context.getParser();
      parser.readToken(ListBeginToken.class);
      if (!"success".equals(parser.readText())) {
        parser.readToken(ListBeginToken.class);
        parser.readToken(ListBeginToken.class);
        final int errorCode = parser.readNumber();
        final String errorMessage = parser.readText();
        parser.skipItems();
        parser.readToken(ListEndToken.class);
        parser.readToken(ListEndToken.class);
        log.error("Received client error: {} {}", errorCode, errorMessage);
        throw new EOFException(errorMessage);
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

    private void updateDir(@NotNull SessionContext context,
                           @NotNull String wcPath,
                           @Nullable VcsFile prevFile,
                           @NotNull VcsFile newFile,
                           @NotNull String parentTokenId,
                           boolean rootDir,
                           @NotNull Depth wcDepth,
                           @NotNull Depth requestedDepth) throws IOException, SVNException {
      final SvnServerWriter writer = context.getWriter();
      final String tokenId;
      final VcsFile oldFile;
      if (rootDir) {
        tokenId = parentTokenId;
        oldFile = prevFile;
      } else {
        tokenId = createTokenId();
        oldFile = sendEntryHeader(context, wcPath, prevFile, newFile, "dir", parentTokenId, tokenId);
      }
      updateProps(writer, "change-dir-prop", tokenId, oldFile, newFile);

      final Depth.Action dirAction = wcDepth.determineAction(requestedDepth, true);
      final Depth.Action fileAction = wcDepth.determineAction(requestedDepth, false);

      final Map<String, VcsFile> oldEntries;
      if (oldFile != null) {
        oldEntries = new HashMap<>();
        for (VcsFile entry : oldFile.getEntries()) {
          oldEntries.put(entry.getFileName(), entry);
        }
      } else {
        oldEntries = Collections.emptyMap();
      }
      final Set<String> forced = new HashSet<>(forcedPaths.getOrDefault(wcPath, Collections.emptySet()));
      for (VcsFile newEntry : newFile.getEntries()) {
        final String entryPath = joinPath(wcPath, newEntry.getFileName());
        final VcsFile oldEntry = getPrevFile(context, entryPath, oldEntries.remove(newEntry.getFileName()));

        final Depth.Action action = newEntry.isDirectory() ? dirAction : fileAction;

        if (!forced.remove(entryPath) && newEntry.equals(oldEntry) && action == Depth.Action.Normal)
          // Same entry.
          continue;

        if (action == Depth.Action.Skip)
          continue;

        final Depth entryDepth = getWcDepth(entryPath, wcDepth);
        updateEntry(context, entryPath, action == Depth.Action.Upgrade ? null : oldEntry, newEntry, tokenId, false, entryDepth, requestedDepth.deepen());
      }
      for (VcsFile entry : oldEntries.values()) {
        final String entryPath = joinPath(wcPath, entry.getFileName());
        removeEntry(context, entryPath, entry.getLastChange().getId(), tokenId);
        forced.remove(entryPath);
      }
      for (String removed : forced) {
        removeEntry(context, removed, newFile.getLastChange().getId(), tokenId);
      }
      if (!rootDir) {
        writer
            .listBegin()
            .word("close-dir")
            .listBegin().string(tokenId).listEnd()
            .listEnd();
      }
    }

    private void updateProps(@NotNull SvnServerWriter writer, @NotNull String command, @NotNull String tokenId, @Nullable VcsFile oldFile, @NotNull VcsFile newFile) throws IOException, SVNException {
      final boolean includeInternalProps = params.isIncludeInternalProps();
      final Map<String, String> oldProps = oldFile != null ? oldFile.getProperties(includeInternalProps) : new HashMap<>();
      for (Map.Entry<String, String> entry : newFile.getProperties(includeInternalProps).entrySet()) {
        if (!entry.getValue().equals(oldProps.remove(entry.getKey()))) {
          changeProp(writer, command, tokenId, entry.getKey(), entry.getValue());
        }
      }
      for (String propName : oldProps.keySet()) {
        changeProp(writer, command, tokenId, propName, null);
      }
    }

    private void updateFile(@NotNull SessionContext context, @NotNull String wcPath, @Nullable VcsFile prevFile, @NotNull VcsFile newFile, @NotNull String parentTokenId) throws IOException, SVNException {
      final SvnServerWriter writer = context.getWriter();
      final String tokenId = createTokenId();
      final VcsFile oldFile = sendEntryHeader(context, wcPath, prevFile, newFile, "file", parentTokenId, tokenId);
      final String md5 = newFile.getMd5();
      if (oldFile == null || !newFile.getContentHash().equals(oldFile.getContentHash())) {
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
            final boolean compress = context.hasCapability("svndiff1");
            final String validateMd5 = deltaGenerator.sendDelta(newFile.getFileName(), source, 0, target, new ISVNDeltaConsumer() {
              private boolean header = true;

              @Override
              public void applyTextDelta(String path, String baseChecksum) throws SVNException {
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
                  throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE, e);
                }
              }

              @Override
              public void textDeltaEnd(String path) throws SVNException {
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
    private InputStream openStream(@Nullable VcsFile file) throws IOException {
      return file == null ? new ByteArrayInputStream(new byte[0]) : file.openStream();
    }

    @NotNull
    private Depth getWcDepth(@NotNull String wcPath, @NotNull Depth parentWcDepth) {
      final SetPathParams params = paths.get(wcPath);
      if (params == null)
        return parentWcDepth.deepen();

      return params.depth;
    }

    @Nullable
    private VcsFile getPrevFile(@NotNull SessionContext context, @NotNull String wcPath, @Nullable VcsFile oldFile) throws IOException, SVNException {
      if (deletedPaths.contains(wcPath))
        return null;

      final SetPathParams pathParams = paths.get(wcPath);
      if (pathParams == null)
        return oldFile;

      if (pathParams.startEmpty || pathParams.rev == 0)
        return null;

      return context.getFile(pathParams.rev, wcPath);
    }

    private void updateEntry(@NotNull SessionContext context,
                             @NotNull String wcPath,
                             @Nullable VcsFile oldFile,
                             @Nullable VcsFile newFile,
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
      else
        updateFile(context, wcPath, oldFile, newFile, parentTokenId);
    }

    private void removeEntry(@NotNull SessionContext context, @NotNull String wcPath, int rev, @NotNull String parentTokenId) throws IOException, SVNException {
      if (deletedPaths.contains(wcPath)) {
        return;
      }
      final SvnServerWriter writer = context.getWriter();
      writer
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

    @Nullable
    private VcsFile sendEntryHeader(@NotNull SessionContext context, @NotNull String wcPath, @Nullable VcsFile oldFile, @NotNull VcsFile newFile, @NotNull String type, @NotNull String parentTokenId, @NotNull String tokenId) throws IOException, SVNException {
      final SvnServerWriter writer = context.getWriter();
      if (oldFile == null) {
        final VcsCopyFrom copyFrom = params.getSendCopyFrom().getCopyFrom(wcPath(""), newFile);
        sendNewEntry(writer, "add-" + type, wcPath, parentTokenId, tokenId, copyFrom);
        if (copyFrom != null) {
          return context.getRepository().getRevisionInfo(copyFrom.getRevision()).getFile(copyFrom.getPath());
        }
      } else {
        sendOpenEntry(writer, "open-" + type, wcPath, parentTokenId, tokenId, oldFile.getLastChange().getId());
      }
      return oldFile;
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

    @NotNull
    private String wcPath(@NotNull String name) {
      return joinPath(params.getPath(), name);
    }

    @NotNull
    private String joinPath(@NotNull String prefix, @NotNull String name) {
      if (name.isEmpty()) return prefix;
      return prefix.isEmpty() ? name : (prefix + "/" + name);
    }

    private void reportCommand(@NotNull SessionContext context) throws IOException, SVNException {
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
        log.error("Unsupported command: {}", cmd);
        BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD, "Unsupported command: " + cmd));
        parser.skipItems();
      }
    }
  }
}
