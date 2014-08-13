package svnserver.repository.git;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.repository.FileInfo;
import svnserver.repository.Repository;
import svnserver.repository.RevisionInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementation for Git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitRepository implements Repository {
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
  private String getObjectMD5(@NotNull ObjectId objectId) throws IOException {
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
  private ObjectLoader openObject(@NotNull ObjectId objectId) throws IOException {
    return repository.newObjectReader().open(objectId);
  }

  @NotNull
  @Override
  public RevisionInfo getRevisionInfo(int revision) throws IOException, SVNException {
    final RevCommit commit = getRevision(revision);
    return new GitRevisionInfo(revision, commit);
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

  private class GitRevisionInfo implements RevisionInfo {
    private final int revision;
    private final RevCommit commit;

    public GitRevisionInfo(int revision, RevCommit commit) {
      this.revision = revision;
      this.commit = commit;
    }

    @Override
    public int getId() {
      return revision;
    }

    @NotNull
    @Override
    public Map<String, String> getProperties() {
      final Map<String, String> props = new HashMap<>();
      props.put(SvnConstants.PROP_AUTHOR, getAuthor());
      props.put(SvnConstants.PROP_LOG, getLog());
      props.put(SvnConstants.PROP_DATE, getDate());
      props.put(SvnConstants.PROP_GIT, commit.name());
      return props;
    }

    @NotNull
    @Override
    public String getDate() {
      return StringHelper.formatDate(TimeUnit.SECONDS.toMillis(commit.getCommitTime()));
    }

    @NotNull
    @Override
    public String getAuthor() {
      return commit.getCommitterIdent().getName();
    }

    @NotNull
    @Override
    public String getLog() {
      return commit.getFullMessage().trim();
    }

    @Nullable
    @Override
    public FileInfo getFile(@NotNull String fullPath) throws IOException {
      if (fullPath.length() == 1) {
        return new GitFileInfo(commit.getTree(), FileMode.TREE, "", this);
      }
      final TreeWalk treeWalk = TreeWalk.forPath(repository, fullPath.substring(1), commit.getTree());
      if (treeWalk == null) {
        return null;
      }
      return new GitFileInfo(treeWalk.getObjectId(0), treeWalk.getFileMode(0), treeWalk.getNameString(), this);
    }
  }

  private class GitFileInfo implements FileInfo {
    @NotNull
    private final ObjectId objectId;
    @NotNull
    private final FileMode fileMode;
    @NotNull
    private final String fileName;
    @NotNull
    private final RevisionInfo lastChange;
    @Nullable
    private ObjectLoader objectLoader;

    public GitFileInfo(@NotNull ObjectId objectId, @NotNull FileMode fileMode, @NotNull String fileName, @NotNull RevisionInfo lastChange) {
      this.objectId = objectId;
      this.fileMode = fileMode;
      this.fileName = fileName;
      this.lastChange = lastChange;
    }

    @NotNull
    @Override
    public String getFileName() {
      return fileName;
    }

    @NotNull
    @Override
    public Map<String, String> getProperties(boolean includeInternalProps) {
      final Map<String, String> props = new HashMap<>();
      if (fileMode.equals(FileMode.EXECUTABLE_FILE)) {
        props.put(SvnConstants.PROP_EXEC, "*");
      } else if (fileMode.equals(FileMode.SYMLINK)) {
        props.put(SvnConstants.PROP_SPECIAL, "*");
      }
      if (includeInternalProps) {
        props.put(SvnConstants.PROP_ENTRY_UUID, uuid);
        props.put(SvnConstants.PROP_ENTRY_REV, String.valueOf(lastChange.getId()));
        props.put(SvnConstants.PROP_ENTRY_DATE, lastChange.getDate());
        props.put(SvnConstants.PROP_ENTRY_AUTHOR, lastChange.getAuthor());
      }
      return props;
    }

    @NotNull
    @Override
    public String getMd5() throws IOException {
      return getObjectMD5(objectId);
    }

    @Override
    public long getSize() throws IOException {
      return getObjectLoader().getSize();
    }

    @Override
    public boolean isDirectory() throws IOException {
      return getKind().equals(SvnConstants.KIND_DIR);
    }

    @NotNull
    @Override
    public String getKind() throws IOException {
      final int objType = fileMode.getObjectType();

      switch (objType) {
        case Constants.OBJ_TREE:
          return SvnConstants.KIND_DIR;
        case Constants.OBJ_BLOB:
          return SvnConstants.KIND_FILE;
        default:
          throw new IllegalStateException("Unknown obj type: " + objType);
      }
    }

    private ObjectLoader getObjectLoader() throws IOException {
      if (objectLoader == null) {
        objectLoader = openObject(objectId);
      }
      return objectLoader;
    }

    @Override
    public void copyTo(@NotNull OutputStream stream) throws IOException {
      getObjectLoader().copyTo(stream);
    }

    @NotNull
    @Override
    public InputStream openStream() throws IOException {
      return getObjectLoader().openStream();
    }

    @NotNull
    @Override
    public Iterable<FileInfo> getEntries() throws IOException {
      final CanonicalTreeParser treeParser = new CanonicalTreeParser(emptyBytes, repository.newObjectReader(), objectId);
      return () -> new Iterator<FileInfo>() {
        @Override
        public boolean hasNext() {
          return !treeParser.eof();
        }

        @Override
        public FileInfo next() {
          final GitFileInfo fileInfo = new GitFileInfo(treeParser.getEntryObjectId(), treeParser.getEntryFileMode(), treeParser.getEntryPathString(), lastChange);
          treeParser.next();
          return fileInfo;
        }
      };
    }

    @NotNull
    @Override
    public RevisionInfo getLastChange() {
      return lastChange;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GitFileInfo that = (GitFileInfo) o;
      return fileMode.equals(that.fileMode)
          && fileName.equals(that.fileName)
          && objectId.equals(that.objectId);
    }

    @Override
    public int hashCode() {
      int result = objectId.hashCode();
      result = 31 * result + fileMode.hashCode();
      result = 31 * result + fileName.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "GitFileInfo{" +
          "fileName='" + fileName + '\'' +
          ", objectId=" + objectId +
          '}';
    }
  }
}
