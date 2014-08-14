package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import svnserver.StringHelper;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.repository.VcsCommitBuilder;
import svnserver.repository.VcsDeltaConsumer;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.*;

/**
 * Commit client changes.
 * <p><pre>
 * get-dir
 * commit
 *    params:   ( logmsg:string ? ( ( lock-path:string lock-token:string ) ... )
 *    keep-locks:bool ? rev-props:proplist )
 *    response: ( )
 *    Upon receiving response, client switches to editor command set.
 *    Upon successful completion of edit, server sends auth-request.
 *    After auth exchange completes, server sends commit-info.
 *    If rev-props is present, logmsg is ignored.  Only the svn:log entry in
 *    rev-props (if any) will be used.
 *    commit-info: ( new-rev:number date:string author:string
 *    ? ( post-commit-err:string ) )
 *    NOTE: when revving this, make 'logmsg' optional, or delete that parameter
 *    and have the log message specified in 'rev-props'.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */

public class CommitCmd extends BaseCmd<CommitCmd.CommitParams> {
  public static class CommitParams {
    @NotNull
    private final String message;

    public CommitParams(@NotNull String message) {
      this.message = message;
    }
  }

  public static class NoParams {
  }

  public static class OpenRootParams {
    @NotNull
    private final int rev[];
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
    private final int rev[];

    public OpenParams(@NotNull String name, @NotNull String parentToken, @NotNull String token, @NotNull int[] rev) {
      this.name = name;
      this.parentToken = parentToken;
      this.token = token;
      this.rev = rev;
    }
  }

  public static class TokenParams {
    @NotNull
    private final String token;

    public TokenParams(@NotNull String token) {
      this.token = token;
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

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(DeltaCmd.class);

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
    log.info("Enter editor mode");
    EditorPipeline pipeline = new EditorPipeline(args);
    pipeline.editorCommand(context);
  }

  public static class CommitFile {
    @NotNull
    private final String fullPath;
    @NotNull
    private final VcsDeltaConsumer deltaConsumer;
    @NotNull
    private final SVNDeltaReader reader = new SVNDeltaReader();

    public CommitFile(@NotNull String fullPath, @NotNull VcsDeltaConsumer deltaConsumer) {
      this.fullPath = fullPath;
      this.deltaConsumer = deltaConsumer;
    }
  }

  @FunctionalInterface
  private static interface VcsCommitConsumer {
    void accept(@NotNull VcsCommitBuilder commitBuilder) throws SVNException, IOException;
  }

  public static class EditorPipeline {
    @NotNull
    private final Map<String, BaseCmd<?>> commands;
    @NotNull
    private final String message;
    @NotNull
    private final Map<String, String> paths;
    @NotNull
    private final Map<String, CommitFile> files;
    @NotNull
    private final Map<String, List<VcsCommitConsumer>> changes;

    public EditorPipeline(@NotNull CommitParams params) {
      this.message = params.message;
      paths = new HashMap<>();
      files = new HashMap<>();
      changes = new HashMap<>();
      commands = new HashMap<>();
      commands.put("add-file", new LambdaCmd<>(OpenParams.class, this::addFile));
      commands.put("open-root", new LambdaCmd<>(OpenRootParams.class, this::openRoot));
      commands.put("open-dir", new LambdaCmd<>(OpenParams.class, this::openDir));
      commands.put("open-file", new LambdaCmd<>(OpenParams.class, this::openFile));
      commands.put("close-dir", new LambdaCmd<>(TokenParams.class, this::closeDir));
      commands.put("close-file", new LambdaCmd<>(ChecksumParams.class, this::closeFile));
      commands.put("textdelta-chunk", new LambdaCmd<>(DeltaChunkParams.class, this::deltaChunk));
      commands.put("textdelta-end", new LambdaCmd<>(TokenParams.class, this::deltaEnd));
      commands.put("apply-textdelta", new LambdaCmd<>(ChecksumParams.class, this::deltaApply));
      commands.put("close-edit", new LambdaCmd<>(NoParams.class, this::closeEdit));
    }

    private void openRoot(@NotNull SessionContext context, @NotNull OpenRootParams args) throws SVNException {
      context.push(this::editorCommand);
      String rootPath = context.getRepositoryPath("");
      String lastPath = rootPath;
      for (int i = rootPath.lastIndexOf('/'); i >= 0; i = rootPath.lastIndexOf('/', i - 1)) {
        final String itemPath = rootPath.substring(0, i);
        final String itemName = lastPath.substring(i + 1);
        final String childPath = lastPath;
        getChanges(itemPath).add(treeBuilder -> {
          treeBuilder.openDir(itemName);
          updateDir(treeBuilder, childPath);
          treeBuilder.closeDir();
        });
        lastPath = itemPath;
      }
      paths.put(args.token, rootPath);
    }

    @NotNull
    private List<VcsCommitConsumer> getChanges(@NotNull String path) {
      List<VcsCommitConsumer> result = changes.get(path);
      if (result == null) {
        result = new ArrayList<>();
        changes.put(path, result);
      }
      return result;
    }

    private void openDir(@NotNull SessionContext context, @NotNull OpenParams args) throws SVNException {
      context.push(this::editorCommand);
      final String fullPath = getPath(context, args.parentToken, args.name);
      getChanges(paths.get(args.parentToken)).add(treeBuilder -> {
        treeBuilder.openDir(StringHelper.baseName(fullPath));
        updateDir(treeBuilder, fullPath);
        treeBuilder.closeDir();
      });
      paths.put(args.token, fullPath);
    }

    @NotNull
    private VcsCommitBuilder updateDir(@NotNull VcsCommitBuilder treeBuilder, @NotNull String fullPath) throws IOException, SVNException {
      for (VcsCommitConsumer consumer : getChanges(fullPath)) {
        consumer.accept(treeBuilder);
      }
      return treeBuilder;
    }

    private void addFile(@NotNull SessionContext context, @NotNull OpenParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      final String path = getPath(context, args.parentToken, args.name);
      log.info("Add file: {} (rev: {})", path);
      files.put(args.token, new CommitFile(path, context.getRepository().createFile(path)));
    }

    private void openFile(@NotNull SessionContext context, @NotNull OpenParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      final String path = getPath(context, args.parentToken, args.name);
      if (args.rev.length == 0) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "File revision is not defined: " + path));
      }
      final int rev = args.rev[0];
      log.info("Modify file: {} (rev: {})", path, rev);
      files.put(args.token, new CommitFile(path, context.getRepository().modifyFile(path, rev)));
    }

    private void closeFile(@NotNull SessionContext context, @NotNull ChecksumParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      if (args.checksum.length == 0) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.DELTA_MD5_CHECKSUM_ABSENT));
      }
      CommitFile file = getFile(args.token);
      file.deltaConsumer.validateChecksum(args.checksum[0]);
      getChanges(StringHelper.parentDir(file.fullPath)).add(treeBuilder -> {
        treeBuilder.saveFile(StringHelper.baseName(file.fullPath), file.deltaConsumer);
      });
    }

    private void deltaApply(@NotNull SessionContext context, @NotNull ChecksumParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      getFile(args.token).deltaConsumer.applyTextDelta(null, args.checksum.length == 0 ? null : args.checksum[0]);
    }

    private void deltaChunk(@NotNull SessionContext context, @NotNull DeltaChunkParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      getFile(args.token).reader.nextWindow(args.chunk, 0, args.chunk.length, "", getFile(args.token).deltaConsumer);
    }

    private void deltaEnd(@NotNull SessionContext context, @NotNull TokenParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      getFile(args.token).deltaConsumer.textDeltaEnd(null);
    }

    @NotNull
    private CommitFile getFile(@NotNull String token) throws SVNException {
      final CommitFile file = files.get(token);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid file token: " + token));
      }
      return file;
    }

    @NotNull
    private String getPath(@NotNull SessionContext context, @NotNull String parentToken, @NotNull String name) throws SVNException {
      final String path = paths.get(parentToken);
      if (path == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid path token: " + parentToken));
      }
      final String fullPath = context.getRepositoryPath(name);
      final String checkPath = StringHelper.joinPath(path, StringHelper.baseName(name));
      if (!checkPath.equals(fullPath)) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.BAD_RELATIVE_PATH, "Invalid path: " + path));
      }
      return fullPath;
    }

    private void closeDir(@NotNull SessionContext context, @NotNull TokenParams args) throws SVNException {
      context.push(this::editorCommand);
      paths.remove(args.token);
    }

    @Deprecated
    private void fake(@NotNull SessionContext context, @NotNull NoParams args) {
      context.push(this::editorCommand);
    }

    private void closeEdit(@NotNull SessionContext context, @NotNull NoParams args) throws IOException, SVNException {
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
      updateDir(new LogCommitBuilder(), "");
      updateDir(context.getRepository().createCommitBuilder(), "").commit(message);
      sendError(writer, 0, "test");
      //context.push(new CheckPermissionStep(this::complete));
    }

    private void complete(@NotNull SessionContext context) throws IOException {
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .number(42) // rev number
          .listBegin().string(StringHelper.formatDate(new Date().getTime())).listEnd() // date
          .listBegin().string("commit author").listEnd()
          .listBegin().listEnd()
          .listEnd();
    }

    private void editorCommand(@NotNull SessionContext context) throws IOException, SVNException {
      final SvnServerParser parser = context.getParser();
      final SvnServerWriter writer = context.getWriter();
      parser.readToken(ListBeginToken.class);
      final String cmd = parser.readText();
      log.info("Editor command: {}", cmd);
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
