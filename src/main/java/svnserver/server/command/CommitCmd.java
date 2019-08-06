/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.eclipse.jetty.util.MultiException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import svnserver.Loggers;
import svnserver.StringHelper;
import svnserver.auth.User;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.repository.VcsConsumer;
import svnserver.repository.git.*;
import svnserver.repository.locks.LockDesc;
import svnserver.repository.locks.LockStorage;
import svnserver.server.SessionContext;
import svnserver.server.step.CheckPermissionStep;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * Commit client changes.
 * <pre>
 *   commit
 *     params:   ( logmsg:string ? ( ( lock-path:string lock-token:string ) ... )
 *                 keep-locks:bool ? rev-props:proplist )
 *     response: ( )
 *     Upon receiving response, client switches to editor command set.
 *     Upon successful completion of edit, server sends auth-request.
 *     After auth exchange completes, server sends commit-info.
 *     If rev-props is present, logmsg is ignored.  Only the svn:log entry in
 *     rev-props (if any) will be used.
 *     commit-info: ( new-rev:number date:string author:string
 *                    ? ( post-commit-err:string ) )
 *     NOTE: when revving this, make 'logmsg' optional, or delete that parameter
 *           and have the log message specified in 'rev-props'.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */

public final class CommitCmd extends BaseCmd<CommitCmd.CommitParams> {
  private static final int MAX_PASS_COUNT = 10;
  @NotNull
  private static final Logger log = Loggers.svn;

  @NotNull
  @Override
  public Class<CommitParams> getArguments() {
    return CommitParams.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull CommitParams args) throws IOException, SVNException {
    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();
    log.debug("Enter editor mode");

    try (EditorPipeline pipeline = new EditorPipeline(context, args)) {
      pipeline.editorCommand(context);
    }
  }

  @Override
  protected void permissionCheck(@NotNull SessionContext context, @NotNull CommitParams args) throws IOException, SVNException {
    context.checkWrite(context.getRepositoryPath(""));
  }

  public static final class LockInfo {

    @NotNull
    private final String path;
    @NotNull
    private final String lockToken;

    public LockInfo(@NotNull String path, @NotNull String lockToken) {
      this.path = path;
      this.lockToken = lockToken;
    }
  }

  public static class CommitParams {
    private final boolean keepLocks;
    @NotNull
    private final String message;
    @NotNull
    private final LockInfo[] locks;

    public CommitParams(@NotNull String message, @NotNull LockInfo[] locks, boolean keepLocks) {
      this.message = message;
      this.locks = locks;
      this.keepLocks = keepLocks;
    }
  }

  public static class OpenRootParams {
    @NotNull
    private final int[] rev;
    @NotNull
    private final String token;

    public OpenRootParams(@NotNull int[] rev, @NotNull String token) {
      this.rev = rev;
      this.token = token;
    }
  }

  public static class OpenParams {
    @NotNull
    private final String name;
    @NotNull
    private final String parentToken;
    @NotNull
    private final String token;
    @NotNull
    private final int[] rev;

    public OpenParams(@NotNull String name, @NotNull String parentToken, @NotNull String token, @NotNull int[] rev) {
      this.name = name;
      this.parentToken = parentToken;
      this.token = token;
      this.rev = rev;
    }
  }

  public static class CopyParams {
    @Nullable
    private final SVNURL copyFrom;
    private final int rev;

    public CopyParams(@NotNull String copyFrom, int rev) throws SVNException {
      this.copyFrom = copyFrom.isEmpty() ? null : SVNURL.parseURIEncoded(copyFrom);
      this.rev = rev;
    }
  }

  public static class AddParams {
    @NotNull
    private final String name;
    @NotNull
    private final String parentToken;
    @NotNull
    private final String token;
    @NotNull
    private final CopyParams copyParams;

    public AddParams(@NotNull String name, @NotNull String parentToken, @NotNull String token, @NotNull CopyParams copyParams) {
      this.name = name;
      this.parentToken = parentToken;
      this.token = token;
      this.copyParams = copyParams;
    }
  }

  public static class DeleteParams {
    @NotNull
    private final String name;
    @NotNull
    private final int[] rev;
    @NotNull
    private final String parentToken;

    public DeleteParams(@NotNull String name, @NotNull int[] rev, @NotNull String parentToken) {
      this.name = name;
      this.rev = rev;
      this.parentToken = parentToken;
    }
  }

  public static class TokenParams {
    @NotNull
    private final String token;

    public TokenParams(@NotNull String token) {
      this.token = token;
    }
  }

  public static class ChangePropParams {
    @NotNull
    private final String token;
    @NotNull
    private final String name;
    @NotNull
    private final String[] value;

    public ChangePropParams(@NotNull String token, @NotNull String name, @NotNull String[] value) {
      this.token = token;
      this.name = name;
      this.value = value;
    }
  }

  public static class ChecksumParams {
    @NotNull
    private final String token;
    @NotNull
    private final String[] checksum;

    public ChecksumParams(@NotNull String token, @NotNull String[] checksum) {
      this.token = token;
      this.checksum = checksum;
    }
  }

  public static class DeltaChunkParams {
    @NotNull
    private final String token;
    @NotNull
    private final byte[] chunk;

    public DeltaChunkParams(@NotNull String token, @NotNull byte[] chunk) {
      this.token = token;
      this.chunk = chunk;
    }
  }

  private static class FileUpdater implements Closeable {
    @NotNull
    private final GitDeltaConsumer deltaConsumer;
    @NotNull
    private final SVNDeltaReader reader = new SVNDeltaReader();

    FileUpdater(@NotNull GitDeltaConsumer deltaConsumer) {
      this.deltaConsumer = deltaConsumer;
    }

    @Override
    public void close() throws IOException {
      deltaConsumer.close();
    }
  }

  private static class EntryUpdater {
    // New parent entry (destination)
    @NotNull
    private final GitEntry entry;
    // Old source entry (source)
    @Nullable
    private final GitFile source;
    @NotNull
    private final Map<String, String> props;
    @NotNull
    private final List<VcsConsumer<GitWriter.GitCommitBuilder>> changes = new ArrayList<>();
    private final boolean head;

    private EntryUpdater(@NotNull GitEntry entry, @Nullable GitFile source, boolean head) throws IOException {
      this.entry = entry;
      this.source = source;
      this.head = head;
      this.props = source == null ? new HashMap<>() : new HashMap<>(source.getProperties());
    }

    @NotNull GitFile getEntry(@NotNull String name) throws IOException, SVNException {
      if (source == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Can't find node: " + name));
      }
      final GitFile file = source.getEntry(name);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Can't find node: " + name + " in " + source.getFullPath()));
      }
      return file;
    }
  }

  private static final class EditorPipeline implements Closeable {
    @NotNull
    private final EntryUpdater rootEntry;
    @NotNull
    private final Map<String, BaseCmd<?>> commands;
    @NotNull
    private final Map<String, BaseCmd<?>> exitCommands;
    @NotNull
    private final String message;
    @NotNull
    private final Map<String, EntryUpdater> paths;
    @NotNull
    private final Map<String, FileUpdater> files;
    @NotNull
    private final Map<String, String> locks;
    @NotNull
    private final GitWriter writer;
    private boolean keepLocks;
    private boolean aborted = false;

    EditorPipeline(@NotNull SessionContext context, @NotNull CommitParams params) throws IOException, SVNException {
      this.message = params.message;
      this.keepLocks = params.keepLocks;
      this.writer = context.getBranch().createWriter(context.getUser());
      final GitFile entry = context.getBranch().getLatestRevision().getFile("");
      if (entry == null) {
        throw new IllegalStateException("Repository root entry not found.");
      }
      this.rootEntry = new EntryUpdater(entry, entry, true);
      paths = new HashMap<>();
      files = new HashMap<>();
      locks = getLocks(context, params.locks);
      commands = new HashMap<>();
      commands.put("add-dir", new LambdaCmd<>(AddParams.class, this::addDir));
      commands.put("add-file", new LambdaCmd<>(AddParams.class, this::addFile));
      commands.put("change-dir-prop", new LambdaCmd<>(ChangePropParams.class, this::changeDirProp));
      commands.put("change-file-prop", new LambdaCmd<>(ChangePropParams.class, this::changeFileProp));
      commands.put("delete-entry", new LambdaCmd<>(DeleteParams.class, this::deleteEntry));
      commands.put("open-root", new LambdaCmd<>(OpenRootParams.class, this::openRoot));
      commands.put("open-dir", new LambdaCmd<>(OpenParams.class, this::openDir));
      commands.put("open-file", new LambdaCmd<>(OpenParams.class, this::openFile));
      commands.put("close-dir", new LambdaCmd<>(TokenParams.class, this::closeDir));
      commands.put("close-file", new LambdaCmd<>(ChecksumParams.class, this::closeFile));
      commands.put("textdelta-chunk", new LambdaCmd<>(DeltaChunkParams.class, this::deltaChunk));
      commands.put("textdelta-end", new LambdaCmd<>(TokenParams.class, this::deltaEnd));
      commands.put("apply-textdelta", new LambdaCmd<>(ChecksumParams.class, this::deltaApply));

      exitCommands = new HashMap<>();
      exitCommands.put("close-edit", new LambdaCmd<>(NoParams.class, this::closeEdit));
      exitCommands.put("abort-edit", new LambdaCmd<>(NoParams.class, this::abortEdit));
    }

    @NotNull
    private static Map<String, String> getLocks(@NotNull SessionContext context, @NotNull LockInfo[] locks) {
      final Map<String, String> result = new HashMap<>();
      for (LockInfo lock : locks) {
        result.put(context.getRepositoryPath(lock.path), lock.lockToken);
      }
      return result;
    }

    private void addDir(@NotNull SessionContext context, @NotNull AddParams args) throws SVNException, IOException {
      final EntryUpdater parent = getParent(args.parentToken);
      final GitFile source;
      context.checkWrite(StringHelper.joinPath(parent.entry.getFullPath(), args.name));
      if (args.copyParams.copyFrom != null) {
        log.debug("Copy dir: {} from {} (rev: {})", args.name, args.copyParams.copyFrom, args.copyParams.rev);
        source = context.getFile(args.copyParams.rev, args.copyParams.copyFrom);
        if (source == null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Directory not found: " + args.copyParams.copyFrom + "@" + args.copyParams.rev));
        }
      } else {
        log.debug("Add dir: {}", args.name);
        source = null;
      }
      final EntryUpdater updater = new EntryUpdater(parent.entry, source, false);
      paths.put(args.token, updater);
      parent.changes.add(treeBuilder -> {
        treeBuilder.addDir(StringHelper.baseName(args.name), source);
        updateDir(treeBuilder, updater);
        treeBuilder.checkDirProperties(updater.props);
        treeBuilder.closeDir();
      });
    }

    private void addFile(@NotNull SessionContext context, @NotNull AddParams args) throws SVNException, IOException {
      final EntryUpdater parent = getParent(args.parentToken);
      final GitDeltaConsumer deltaConsumer;
      final String fullPath = StringHelper.joinPath(parent.entry.getFullPath(), args.name);
      context.checkWrite(fullPath);
      if (args.copyParams.copyFrom != null) {
        log.debug("Copy file: {} from {} (rev: {})", fullPath, args.copyParams.copyFrom, args.copyParams.rev);
        final GitFile file = context.getFile(args.copyParams.rev, args.copyParams.copyFrom);
        if (file == null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Can't find path: " + args.copyParams.copyFrom + "@" + args.copyParams.rev));
        }
        deltaConsumer = writer.modifyFile(parent.entry, args.name, file);
      } else {
        log.debug("Add file: {}", parent);
        deltaConsumer = writer.createFile(parent.entry, args.name);
      }
      files.put(args.token, new FileUpdater(deltaConsumer));
      parent.changes.add(treeBuilder -> treeBuilder.saveFile(StringHelper.baseName(args.name), deltaConsumer, false));
    }

    private void changeDirProp(@NotNull SessionContext context, @NotNull ChangePropParams args) throws SVNException {
      final EntryUpdater dir = paths.get(args.token);
      if (dir == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid path token: " + args.token));
      }
      changeProp(dir.props, args);
    }

    private void changeFileProp(@NotNull SessionContext context, @NotNull ChangePropParams args) throws SVNException {
      changeProp(getFile(args.token).deltaConsumer.getProperties(), args);
    }

    private void deleteEntry(@NotNull SessionContext context, @NotNull DeleteParams args) throws SVNException, IOException {
      final EntryUpdater parent = getParent(args.parentToken);
      final int rev = args.rev.length > 0 ? args.rev[0] : -1;
      context.checkWrite(StringHelper.joinPath(parent.entry.getFullPath(), args.name));
      log.debug("Delete entry: {} (rev: {})", args.name, rev);
      final GitFile entry = parent.getEntry(StringHelper.baseName(args.name));
      if (parent.head && (rev >= 0) && (parent.source != null)) {
        checkUpToDate(entry, rev, true);
      }
      parent.changes.add(treeBuilder -> treeBuilder.delete(entry.getFileName()));
    }

    private void openRoot(@NotNull SessionContext context, @NotNull OpenRootParams args) throws SVNException, IOException {
      final String fullPath = context.getRepositoryPath("");
      final String[] rootPath = fullPath.split("/");
      EntryUpdater lastUpdater = rootEntry;
      for (int i = 1; i < rootPath.length; ++i) {
        String name = rootPath[i];
        final GitFile entry = lastUpdater.getEntry(name);
        final EntryUpdater updater = new EntryUpdater(entry, entry, true);
        lastUpdater.changes.add(treeBuilder -> {
          treeBuilder.openDir(name);
          updateDir(treeBuilder, updater);
          treeBuilder.closeDir();
        });
        lastUpdater = updater;
      }
      final int rev = args.rev.length > 0 ? args.rev[0] : -1;
      if (rev >= 0) {
        if (lastUpdater.source == null) {
          throw new IllegalStateException();
        }
        checkUpToDate(lastUpdater.source, rev, false);
        final Map<String, String> props = lastUpdater.props;
        lastUpdater.changes.add(treeBuilder -> treeBuilder.checkDirProperties(props));
      }
      paths.put(args.token, lastUpdater);
    }

    private void openDir(@NotNull SessionContext context, @NotNull OpenParams args) throws SVNException, IOException {
      final EntryUpdater parent = getParent(args.parentToken);
      final int rev = args.rev.length > 0 ? args.rev[0] : -1;
      log.debug("Modify dir: {} (rev: {})", args.name, rev);
      final GitFile sourceDir = parent.getEntry(StringHelper.baseName(args.name));
      context.checkRead(sourceDir.getFullPath());
      final EntryUpdater dir = new EntryUpdater(sourceDir, sourceDir, parent.head);
      if ((rev >= 0) && (parent.head)) {
        checkUpToDate(sourceDir, rev, false);
      }
      paths.put(args.token, dir);
      parent.changes.add(treeBuilder -> {
        treeBuilder.openDir(StringHelper.baseName(args.name));
        updateDir(treeBuilder, dir);
        if (rev >= 0) {
          treeBuilder.checkDirProperties(dir.props);
        }
        treeBuilder.closeDir();
      });
    }

    private void openFile(@NotNull SessionContext context, @NotNull OpenParams args) throws SVNException, IOException {
      final EntryUpdater parent = getParent(args.parentToken);
      final int rev = args.rev.length > 0 ? args.rev[0] : -1;
      context.checkWrite(StringHelper.joinPath(parent.entry.getFullPath(), args.name));
      log.debug("Modify file: {} (rev: {})", args.name, rev);
      GitFile vcsFile = parent.getEntry(StringHelper.baseName(args.name));
      final GitDeltaConsumer deltaConsumer = writer.modifyFile(parent.entry, vcsFile.getFileName(), vcsFile);
      files.put(args.token, new FileUpdater(deltaConsumer));
      if (parent.head && (rev >= 0)) {
        checkUpToDate(vcsFile, rev, true);
      }
      parent.changes.add(treeBuilder -> treeBuilder.saveFile(StringHelper.baseName(args.name), deltaConsumer, true));
    }

    private void closeDir(@NotNull SessionContext context, @NotNull TokenParams args) {
      paths.remove(args.token);
    }

    private void closeFile(@NotNull SessionContext context, @NotNull ChecksumParams args) throws SVNException {
      final FileUpdater file = files.remove(args.token);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid file token: " + args.token));
      }
      if (args.checksum.length != 0) {
        file.deltaConsumer.validateChecksum(args.checksum[0]);
      }
    }

    private void deltaChunk(@NotNull SessionContext context, @NotNull DeltaChunkParams args) throws SVNException {
      getFile(args.token).reader.nextWindow(args.chunk, 0, args.chunk.length, "", getFile(args.token).deltaConsumer);
    }

    private void deltaEnd(@NotNull SessionContext context, @NotNull TokenParams args) throws SVNException {
      getFile(args.token).deltaConsumer.textDeltaEnd(null);
    }

    private void deltaApply(@NotNull SessionContext context, @NotNull ChecksumParams args) throws SVNException {
      getFile(args.token).deltaConsumer.applyTextDelta(null, args.checksum.length == 0 ? null : args.checksum[0]);
    }

    private void closeEdit(@NotNull SessionContext context, @NotNull NoParams args) throws IOException, SVNException {
      if (context.getUser().isAnonymous()) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Anonymous users cannot create commits"));
      }
      if (!paths.isEmpty()) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Found not closed directory tokens: " + paths.keySet()));
      }
      if (!files.isEmpty()) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Found not closed file tokens: " + files.keySet()));
      }
      final GitRevision revision = context.getBranch().getRepository().wrapLockWrite(lockStorage -> {
        final List<LockDesc> oldLocks = keepLocks ? getLocks(context.getUser(), lockStorage, locks) : Collections.emptyList();
        for (int pass = 0; ; ++pass) {
          if (pass >= MAX_PASS_COUNT) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Can't commit changes to upstream repository."));
          }
          final GitRevision newRevision = updateDir(writer.createCommitBuilder(lockStorage, locks), rootEntry).commit(context.getUser(), message);
          if (newRevision != null) {
            lockStorage.renewLocks(context.getBranch(), oldLocks.toArray(LockDesc.emptyArray));
            return newRevision;
          }
        }
      });
      context.push(new CheckPermissionStep((svnContext) -> complete(svnContext, revision), null));
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
    }

    private void abortEdit(@NotNull SessionContext context, @NotNull NoParams args) throws IOException {
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
    }

    @NotNull
    private EntryUpdater getParent(@NotNull String parentToken) throws SVNException {
      final EntryUpdater parent = paths.get(parentToken);
      if (parent == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid path token: " + parentToken));
      }
      return parent;
    }

    @NotNull
    private GitWriter.GitCommitBuilder updateDir(@NotNull GitWriter.GitCommitBuilder treeBuilder, @NotNull EntryUpdater updater) throws IOException, SVNException {
      for (VcsConsumer<GitWriter.GitCommitBuilder> consumer : updater.changes) {
        consumer.accept(treeBuilder);
      }
      return treeBuilder;
    }

    private void changeProp(@NotNull Map<String, String> props, @NotNull ChangePropParams args) {
      if (args.value.length > 0) {
        props.put(args.name, args.value[0]);
      } else {
        props.remove(args.name);
      }
    }

    @NotNull
    private FileUpdater getFile(@NotNull String token) throws SVNException {
      final FileUpdater file = files.get(token);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid file token: " + token));
      }
      return file;
    }

    private void checkUpToDate(@NotNull GitFile vcsFile, int rev, boolean checkLock) throws SVNException {
      if (vcsFile.getLastChange().getId() > rev) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "Working copy is not up-to-date: " + vcsFile.getFullPath()));
      }
      rootEntry.changes.add(treeBuilder -> treeBuilder.checkUpToDate(vcsFile.getFullPath(), rev, checkLock));
    }

    @NotNull
    private List<LockDesc> getLocks(@NotNull User user, @NotNull LockStorage lockManager, @NotNull Map<String, String> locks) throws IOException {
      final List<LockDesc> result = new ArrayList<>();
      for (Map.Entry<String, String> entry : locks.entrySet()) {
        for (LockDesc lock : lockManager.getLocks(user, writer.getBranch(), entry.getKey(), entry.getValue())) {
          if (lock.getToken().equals(entry.getValue())) {
            result.add(lock);
          }
        }
      }
      return result;
    }

    private void complete(@NotNull SessionContext context, @NotNull GitRevision revision) throws IOException {
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .number(revision.getId()) // rev number
          .stringNullable(revision.getDateString()) // date
          .stringNullable(revision.getAuthor())
          .listBegin().listEnd()
          .listEnd();
    }

    @SuppressWarnings("unchecked")
    private void editorCommand(@NotNull SessionContext context) throws IOException, SVNException {
      final SvnServerParser parser = context.getParser();
      parser.readToken(ListBeginToken.class);
      final String cmd = parser.readText();
      log.debug("Editor command: {}", cmd);

      BaseCmd command = exitCommands.get(cmd);
      if (command == null) {
        context.push(this::editorCommand);
        command = commands.get(cmd);
      }

      if (command == null) {
        context.skipUnsupportedCommand(cmd);
        return;
      }

      if (aborted) {
        parser.skipItems();
        return;
      }

      try {
        Object param = MessageParser.parse(command.getArguments(), parser);
        parser.readToken(ListEndToken.class);
        command.process(context, param);
      } catch (SVNException e) {
        aborted = true;
        throw e;
      } catch (Throwable e) {
        log.warn("Exception during in cmd " + cmd, e);
        aborted = true;
        throw e;
      }
    }

    @Override
    public void close() throws IOException {
      final MultiException multiException = new MultiException();

      for (FileUpdater updater : files.values()) {
        try {
          updater.close();
        } catch (IOException e) {
          multiException.add(e);
        }
      }

      try {
        multiException.ifExceptionThrow();
      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }
}
