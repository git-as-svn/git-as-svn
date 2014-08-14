package svnserver.repository.git;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import svnserver.StringHelper;
import svnserver.repository.VcsDeltaConsumer;
import svnserver.repository.VcsRepository;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation for Git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitRepository implements VcsRepository {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepository.class);
  @NotNull
  public static final byte[] emptyBytes = new byte[0];
  @NotNull
  private final FileRepository repository;
  @NotNull
  private final List<RevCommit> revisions;
  @NotNull
  private final String uuid;
  @NotNull
  private final Map<String, String> cacheMd5 = new ConcurrentHashMap<>();

  public GitRepository(@NotNull String uuid) throws IOException {
    this.uuid = uuid;
    this.repository = new FileRepository(findGitPath());
    this.revisions = loadRevisions(repository);
  }

  private static List<RevCommit> loadRevisions(@NotNull FileRepository repository) throws IOException {
    final Ref master = repository.getRef("master");
    final LinkedList<RevCommit> revisions = new LinkedList<>();
    final RevWalk revWalk = new RevWalk(repository);
    ObjectId objectId = master.getObjectId();
    while (true) {
      final RevCommit commit = revWalk.parseCommit(objectId);
      revisions.addFirst(commit);
      if (commit.getParentCount() == 0) break;
      objectId = commit.getParent(0);
    }
    revisions.addFirst(getEmptyCommit(repository));
    return new ArrayList<>(revisions);
  }

  private File findGitPath() {
    final File root = new File(".").getAbsoluteFile();
    File path = root;
    while (true) {
      final File repo = new File(path, ".git");
      if (repo.exists()) {
        return repo;
      }
      path = path.getParentFile();
      if (path == null) {
        throw new IllegalStateException("Repository not found from directiry: " + root.getAbsolutePath());
      }
    }
  }

  @Override
  public int getLatestRevision() throws IOException {
    return revisions.size() - 1;
  }

  @NotNull
  @Override
  public String getUuid() {
    return uuid;
  }

  @NotNull
  public FileRepository getRepository() {
    return repository;
  }

  @NotNull
  public String getObjectMD5(@NotNull ObjectId objectId) throws IOException {
    //repository.newObjectReader().open(
    String result = cacheMd5.get(objectId.name());
    if (result == null) {
      final byte[] buffer = new byte[64 * 1024];
      final MessageDigest md5 = getMd5();
      try (InputStream stream = openObject(objectId).openStream()) {
        while (true) {
          int size = stream.read(buffer);
          if (size < 0) break;
          md5.update(buffer, 0, size);
        }
      }
      result = StringHelper.toHex(md5.digest());
      cacheMd5.putIfAbsent(objectId.name(), result);
    }
    return result;
  }

  @NotNull
  public ObjectLoader openObject(@NotNull ObjectId objectId) throws IOException {
    return repository.newObjectReader().open(objectId);
  }

  @NotNull
  @Override
  public GitRevision getRevisionInfo(int revision) throws IOException, SVNException {
    final RevCommit commit = getRevision(revision);
    return new GitRevision(this, revision, commit);
  }

  private static MessageDigest getMd5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private RevCommit getRevision(int revision) throws SVNException {
    if (revision >= revisions.size())
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revision));
    return revisions.get(revision);
  }

  @NotNull
  private static RevCommit getEmptyCommit(@NotNull FileRepository repository) throws IOException {
    final ObjectInserter inserter = repository.newObjectInserter();
    final TreeFormatter treeBuilder = new TreeFormatter();
    final ObjectId treeId = inserter.insert(treeBuilder);

    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(new PersonIdent("", "", 0, 0));
    commitBuilder.setCommitter(new PersonIdent("", "", 0, 0));
    commitBuilder.setMessage("");
    commitBuilder.setTreeId(treeId);
    final ObjectId commitId = inserter.insert(commitBuilder);
    final RevWalk revWalk = new RevWalk(repository);

    return revWalk.parseCommit(commitId);
  }

  @NotNull
  @Override
  public VcsDeltaConsumer createFile(@NotNull String fullPath) throws IOException, SVNException {
    // todo: Check revision for prevent change override.
    final GitFile file = getRevisionInfo(getLatestRevision()).getFile(fullPath);
    if (file != null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "File is not up-to-date: " + fullPath));
    }
    return new GitDeltaConsumer(null, fullPath);
  }

  @NotNull
  @Override
  public VcsDeltaConsumer modifyFile(@NotNull String fullPath, int revision) throws IOException, SVNException {
    // todo: Check revision for prevent change override.
    final GitFile file = getRevisionInfo(getLatestRevision()).getFile(fullPath);
    if (file == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, fullPath));
    }
    if (file.getLastChange().getId() > revision) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "File is not up-to-date: " + fullPath));
    }
    return new GitDeltaConsumer(file, fullPath);
  }

  private class GitDeltaConsumer implements VcsDeltaConsumer {
    @Nullable
    private final GitFile file;
    private final String fullPath;
    @Nullable
    private SVNDeltaProcessor window;
    @Nullable
    private ObjectId objectId;
    // todo: Wrap output stream for saving big blob to temporary files.
    @NotNull
    private ByteArrayOutputStream memory;

    public GitDeltaConsumer(@Nullable GitFile file, String fullPath) {
      this.file = file;
      this.fullPath = fullPath;
      objectId = file != null ? file.getObjectId() : null;
      memory = new ByteArrayOutputStream();
    }

    @Override
    public void applyTextDelta(String path, @Nullable String baseChecksum) throws SVNException {
      try {
        if ((file != null) && (baseChecksum != null)) {
          if (!baseChecksum.equals(file.getMd5())) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
          }
        }
        if (window != null) {
          throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
        }
        window = new SVNDeltaProcessor();
        window.applyTextDelta(file != null ? file.openStream() : new ByteArrayInputStream(emptyBytes), memory, true);
      } catch (IOException e) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
      }
    }

    @Override
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
      if (window == null) {
        throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
      }
      return window.textDeltaChunk(diffWindow);
    }

    @Override
    public void textDeltaEnd(String path) throws SVNException {
      try {
        if (window == null) {
          throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
        }
        objectId = repository.newObjectInserter().insert(Constants.OBJ_BLOB, memory.toByteArray());
        log.info("Created blob {} for file: {}", objectId.getName(), fullPath);
      } catch (IOException e) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
      }
    }

    @Override
    public void validateChecksum(@NotNull String md5) throws SVNException {
      try {
        if (window != null) {
          if (!md5.equals(window.textDeltaEnd())) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
          }
        } else if (file != null) {
          if (!md5.equals(file.getMd5())) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
          }
        }
      } catch (IOException e) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
      }
    }
  }
}
