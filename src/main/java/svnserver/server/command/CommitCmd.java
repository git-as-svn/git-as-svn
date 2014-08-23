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
import svnserver.repository.*;
import svnserver.server.SessionContext;
import svnserver.server.step.CheckPermissionStep;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  public static class CopyParams {
    @NotNull
    private final String copyFrom;
    private final int rev;

    public CopyParams(@NotNull String copyFrom, int rev) {
      this.copyFrom = copyFrom;
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
    private final int rev[];
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

  private static final int MAX_PASS_COUNT = 10;
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
    private final VcsDeltaConsumer deltaConsumer;
    @NotNull
    private final SVNDeltaReader reader = new SVNDeltaReader();

    public CommitFile(@NotNull VcsDeltaConsumer deltaConsumer) {
      this.deltaConsumer = deltaConsumer;
    }
  }

  public static class EditorPipeline {
    @NotNull
    private final Map<String, BaseCmd<?>> commands;
    @NotNull
    private final String message;
    @NotNull
    private final Map<String, VcsDirectoryConsumer> paths;
    @NotNull
    private final Map<String, CommitFile> files;
    @NotNull
    private final Map<String, List<VcsConsumer<VcsCommitBuilder>>> changes;

    public EditorPipeline(@NotNull CommitParams params) {
      this.message = params.message;
      paths = new HashMap<>();
      files = new HashMap<>();
      changes = new HashMap<>();
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
      commands.put("close-edit", new LambdaCmd<>(NoParams.class, this::closeEdit));
    }

    private void changeDirProp(@NotNull SessionContext context, @NotNull ChangePropParams args) throws SVNException {
      context.push(this::editorCommand);
      final VcsDirectoryConsumer dir = paths.get(args.token);
      if (dir == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid path token: " + args.token));
      }
      Map<String, String> props = dir.getProperties();
      if (args.value.length > 0) {
        props.put(args.name, args.value[0]);
      } else {
        props.remove(args.name);
      }
    }

    private void changeFileProp(@NotNull SessionContext context, @NotNull ChangePropParams args) throws SVNException {
      context.push(this::editorCommand);
      final CommitFile file = getFile(args.token);
      final Map<String, String> props = file.deltaConsumer.getProperties();
      if (args.value.length > 0) {
        props.put(args.name, args.value[0]);
      } else {
        props.remove(args.name);
      }
    }

    private void openRoot(@NotNull SessionContext context, @NotNull OpenRootParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      String rootPath = context.getRepositoryPath("");
      String lastPath = rootPath;
      for (int i = rootPath.lastIndexOf('/'); i >= 0; i = rootPath.lastIndexOf('/', i - 1)) {
        final String itemPath = rootPath.substring(0, i);
        final String childPath = lastPath;
        final VcsDirectoryConsumer dir = context.getRepository().modifyDir(lastPath, -1);
        getChanges(itemPath).add(treeBuilder -> {
          treeBuilder.openDir(dir);
          updateDir(treeBuilder, childPath);
          treeBuilder.closeDir();
        });
        lastPath = itemPath;
      }
      final int rev = args.rev.length > 0 ? args.rev[0] : -1;
      paths.put(args.token, context.getRepository().modifyDir(rootPath, rev));
    }

    @NotNull
    private List<VcsConsumer<VcsCommitBuilder>> rootChanges() {
      return getChanges("");
    }

    @NotNull
    private List<VcsConsumer<VcsCommitBuilder>> getChanges(@NotNull String path) {
      return changes.computeIfAbsent(path, s -> new ArrayList<>());
    }

    private void openDir(@NotNull SessionContext context, @NotNull OpenParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      final String path = getPath(context, args.parentToken, args.name);
      final int rev = args.rev.length > 0 ? args.rev[0] : -1;
      log.info("Modify file: {} (rev: {})", path, rev);
      final VcsDirectoryConsumer dir = context.getRepository().modifyDir(path, rev);
      paths.put(args.token, dir);
      getChanges(paths.get(args.parentToken).getPath()).add(treeBuilder -> {
        treeBuilder.openDir(dir);
        updateDir(treeBuilder, path);
        treeBuilder.closeDir();
      });
    }

    @NotNull
    private VcsCommitBuilder updateDir(@NotNull VcsCommitBuilder treeBuilder, @NotNull String fullPath) throws IOException, SVNException {
      for (VcsConsumer<VcsCommitBuilder> consumer : getChanges(fullPath)) {
        consumer.accept(treeBuilder);
      }
      return treeBuilder;
    }

    private void addDir(@NotNull SessionContext context, @NotNull AddParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      final String path = getPath(context, args.parentToken, args.name);
      final VcsDirectoryConsumer dir;
      if (args.copyParams.rev != 0) {
        log.info("Copy dir: {} (rev: {}) from {} (rev: {})", path, args.copyParams.copyFrom, args.copyParams.rev);
        dir = context.getRepository().copyDir(path, context.getRepositoryPath(args.copyParams.copyFrom), args.copyParams.rev);
      } else {
        log.info("Add dir: {} (rev: {})", path);
        dir = context.getRepository().createDir(path);
      }
      log.info("Add dir: {} (rev: {})", path);
      getChanges(paths.get(args.parentToken).getPath()).add(treeBuilder -> {
        treeBuilder.addDir(StringHelper.baseName(path), dir);
        updateDir(treeBuilder, path);
        treeBuilder.closeDir();
      });
      paths.put(args.token, dir);
    }

    private void addFile(@NotNull SessionContext context, @NotNull AddParams args) throws SVNException, IOException {
      final String path = getPath(context, args.parentToken, args.name);
      final CommitFile file;
      if (args.copyParams.rev != 0) {
        log.info("Copy file: {} (rev: {}) from {} (rev: {})", path, args.copyParams.copyFrom, args.copyParams.rev);
        file = new CommitFile(context.getRepository().copyFile(path, context.getRepositoryPath(args.copyParams.copyFrom), args.copyParams.rev));
      } else {
        log.info("Add file: {} (rev: {})", path);
        file = new CommitFile(context.getRepository().createFile(path));
      }
      files.put(args.token, file);
      context.push(this::editorCommand);
    }

    private void deleteEntry(@NotNull SessionContext context, @NotNull DeleteParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      final String path = getPath(context, args.parentToken, args.name);
      if (args.rev.length == 0) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "File revision is not defined: " + path));
      }
      final int rev = args.rev[0];
      final VcsFile file = context.getRepository().deleteEntry(path, rev);
      log.info("Delete entry: {} (rev: {})", path, rev);
      getChanges(paths.get(args.parentToken).getPath()).add(treeBuilder -> treeBuilder.delete(StringHelper.baseName(path), file));
    }

    private void openFile(@NotNull SessionContext context, @NotNull OpenParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      final String path = getPath(context, args.parentToken, args.name);
      if (args.rev.length == 0) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "File revision is not defined: " + path));
      }
      final int rev = args.rev[0];
      log.info("Modify file: {} (rev: {})", path, rev);
      files.put(args.token, new CommitFile(context.getRepository().modifyFile(path, rev)));
      rootChanges().add(treeBuilder -> treeBuilder.checkUpToDate(path, rev));
    }

    private void closeFile(@NotNull SessionContext context, @NotNull ChecksumParams args) throws SVNException, IOException {
      context.push(this::editorCommand);
      final CommitFile file = files.remove(args.token);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid file token: " + args.token));
      }
      if (args.checksum.length != 0) {
        file.deltaConsumer.validateChecksum(args.checksum[0]);
      }
      final String path = file.deltaConsumer.getPath();
      getChanges(StringHelper.parentDir(path)).add(treeBuilder -> treeBuilder.saveFile(file.deltaConsumer));
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
      final VcsDirectoryConsumer dir = paths.get(parentToken);
      if (dir == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid path token: " + parentToken));
      }
      final String fullPath = context.getRepositoryPath(name);
      final String checkPath = StringHelper.joinPath(dir.getPath(), StringHelper.baseName(name));
      if (!checkPath.equals(fullPath)) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.BAD_RELATIVE_PATH, "Invalid path: " + dir.getPath()));
      }
      return fullPath;
    }

    private void closeDir(@NotNull SessionContext context, @NotNull TokenParams args) throws SVNException {
      context.push(this::editorCommand);
      paths.remove(args.token);
    }

    private void closeEdit(@NotNull SessionContext context, @NotNull NoParams args) throws IOException, SVNException {
      if (context.getUser().isAnonymous()) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Anonymous users cannot create commits"));
      }
      if (!paths.isEmpty()) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Found not closed directory token: " + paths.keySet().iterator().next()));
      }
      if (!files.isEmpty()) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Found not closed file token: " + paths.keySet().iterator().next()));
      }
      for (int pass = 0; ; ++pass) {
        if (pass >= MAX_PASS_COUNT) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Cant commit changes to upstream repositroy."));
        }
        final VcsRevision revision = updateDir(context.getRepository().createCommitBuilder(), "").commit(context.getUser(), message);
        if (revision != null) {
          // todo: CheckPermissionStep must be before commit
          context.push(new CheckPermissionStep((svnContext) -> complete(svnContext, revision)));
          break;
        }
      }
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
    }

    private void complete(@NotNull SessionContext context, @NotNull VcsRevision revision) throws IOException, SVNException {
      final SvnServerWriter writer = context.getWriter();
      writer
          .listBegin()
          .number(revision.getId()) // rev number
          .listBegin().string(revision.getDate()).listEnd() // date
          .listBegin().string(revision.getAuthor()).listEnd()
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
