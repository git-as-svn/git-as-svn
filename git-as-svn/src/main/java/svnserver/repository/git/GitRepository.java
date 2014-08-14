package svnserver.repository.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.repository.UserInfo;
import svnserver.repository.VcsCommitBuilder;
import svnserver.repository.VcsDeltaConsumer;
import svnserver.repository.VcsRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  @NotNull
  private final String uuid;
  @NotNull
  private final String branch;
  @NotNull
  private final Map<String, String> cacheMd5 = new ConcurrentHashMap<>();

  public GitRepository(@NotNull String uuid, @NotNull String branch) throws IOException {
    this.uuid = uuid;
    this.repository = new FileRepository(findGitPath());
    this.branch = repository.getRef(branch).getName();
    this.revisions = new ArrayList<>(Arrays.asList(getEmptyCommit(repository)));
    updateRevisions();
  }

  @Override
  public void updateRevisions() throws IOException {
    // Fast check.
    lock.readLock().lock();
    try {
      final ObjectId lastCommitId = revisions.get(revisions.size() - 1);
      final Ref master = repository.getRef(branch);
      if (master.getObjectId().equals(lastCommitId)) {
        return;
      }
    } finally {
      lock.readLock().unlock();
    }
    // Real update.
    lock.writeLock().lock();
    try {
      final ObjectId lastCommitId = revisions.get(revisions.size() - 1);
      final Ref master = repository.getRef(branch);
      final List<RevCommit> newRevs = new ArrayList<>();
      final RevWalk revWalk = new RevWalk(repository);
      ObjectId objectId = master.getObjectId();
      while (true) {
        if (lastCommitId.equals(objectId)) {
          break;
        }
        final RevCommit commit = revWalk.parseCommit(objectId);
        newRevs.add(commit);
        if (commit.getParentCount() == 0) break;
        objectId = commit.getParent(0);
      }
      for (int i = newRevs.size() - 1; i >= 0; i--) {
        revisions.add(newRevs.get(i));
      }
    } finally {
      lock.writeLock().unlock();
    }
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
    lock.readLock().lock();
    try {
      return revisions.size() - 1;
    } finally {
      lock.readLock().unlock();
    }
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
  private GitRevision getRevision(ObjectId revisionId) throws SVNException {
    lock.readLock().lock();
    try {
      for (int i = revisions.size() - 1; i >= 0; i--) {
        RevCommit revision = revisions.get(i);
        if (revision.equals(revisionId)) {
          return new GitRevision(this, i, revision);
        }
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revisionId.name()));
    } finally {
      lock.readLock().unlock();
    }
  }

  @NotNull
  private RevCommit getRevision(int revision) throws SVNException {
    lock.readLock().lock();
    try {
      if (revision >= revisions.size())
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revision));
      return revisions.get(revision);
    } finally {
      lock.readLock().unlock();
    }
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
    final GitFile file = getRevisionInfo(getLatestRevision()).getFile(fullPath);
    if (file != null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "File is not up-to-date: " + fullPath));
    }
    return new GitDeltaConsumer(this, null, fullPath);
  }

  @NotNull
  @Override
  public VcsDeltaConsumer modifyFile(@NotNull String fullPath, int revision) throws IOException, SVNException {
    final GitFile file = getRevisionInfo(getLatestRevision()).getFile(fullPath);
    if (file == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, fullPath));
    }
    if (file.getLastChange().getId() > revision) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "File is not up-to-date: " + fullPath));
    }
    return new GitDeltaConsumer(this, file, fullPath);
  }

  @NotNull
  @Override
  public VcsCommitBuilder createCommitBuilder() throws IOException {
    final Ref branchRef = repository.getRef(branch);
    final ObjectId objectId = branchRef.getObjectId();
    final RevWalk revWalk = new RevWalk(repository);
    final ObjectReader reader = revWalk.getObjectReader();
    final ObjectInserter inserter = repository.newObjectInserter();
    final RevCommit commit = revWalk.parseCommit(objectId);

    final Deque<GitTreeUpdate> treeStack = new ArrayDeque<>();
    treeStack.push(new GitTreeUpdate("", reader, commit.getTree()));

    return new VcsCommitBuilder() {
      @Override
      public void addDir(@NotNull String name) throws IOException, SVNException {
        final GitTreeUpdate current = treeStack.element();
        if (current.entries.containsKey(name)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, getFullPath(name)));
        }
        treeStack.push(new GitTreeUpdate(name));
      }

      @Override
      public void openDir(@NotNull String name) throws SVNException, IOException {
        final GitTreeUpdate current = treeStack.element();
        final GitTreeEntry originalDir = current.entries.remove(name);
        if ((originalDir == null) || (!originalDir.fileMode.equals(FileMode.TREE))) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
        }
        treeStack.push(new GitTreeUpdate(name, reader, originalDir.objectId));
      }

      @Override
      public void closeDir() throws SVNException, IOException {
        final GitTreeUpdate last = treeStack.pop();
        final GitTreeUpdate current = treeStack.element();
        final String fullPath = getFullPath(last.name);
        if (last.entries.isEmpty()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Empty directories is not supported: " + fullPath));
        }
        final ObjectId subtreeId = last.buildTree(inserter);
        log.info("Create tree {} for dir: {}", subtreeId.name(), fullPath);
        if (current.entries.put(last.name, new GitTreeEntry(FileMode.TREE, subtreeId)) != null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, fullPath));
        }
      }

      @Override
      public void saveFile(@NotNull String name, @NotNull VcsDeltaConsumer deltaConsumer) throws SVNException {
        final GitDeltaConsumer gitDeltaConsumer = (GitDeltaConsumer) deltaConsumer;
        final GitTreeUpdate current = treeStack.element();
        final GitTreeEntry entry = current.entries.get(name);
        final ObjectId originalId = gitDeltaConsumer.getOriginalId();
        if ((originalId != null) && (entry == null)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
        } else if ((originalId != null) && (!entry.objectId.equals(originalId))) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, getFullPath(name)));
        } else if ((originalId == null) && (entry != null)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, getFullPath(name)));
        }
        final ObjectId objectId = gitDeltaConsumer.getObjectId();
        if (objectId == null) {
          // Content not updated.
          if (originalId == null) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, getFullPath(name)));
          }
          return;
        }
        current.entries.put(name, new GitTreeEntry(gitDeltaConsumer.getFileMode(), objectId));
      }

      @Override
      public GitRevision commit(@NotNull UserInfo userInfo, @NotNull String message) throws SVNException, IOException {
        final GitTreeUpdate root = treeStack.element();
        final ObjectId treeId = root.buildTree(inserter);
        log.info("Create tree {} for commit.", treeId.name());

        final CommitBuilder commitBuilder = new CommitBuilder();
        commitBuilder.setAuthor(new PersonIdent(userInfo.getName(), userInfo.getMail()));
        commitBuilder.setCommitter(new PersonIdent(userInfo.getName(), userInfo.getMail()));
        commitBuilder.setMessage(message);
        commitBuilder.setParentId(commit.getId());
        commitBuilder.setTreeId(treeId);
        final ObjectId commitId = inserter.insert(commitBuilder);

        log.info("Create commit {}: {}", commitId.name(), message);

        try {
          log.info("Try to push commit in branch: {}", branchRef);
          Iterable<PushResult> results = new Git(repository)
              .push()
              .setRemote(".")
              .setRefSpecs(new RefSpec(commitId.name() + ":" + branchRef.getName()))
              .call();
          for (PushResult result : results) {
            for (RemoteRefUpdate remoteUpdate : result.getRemoteUpdates()) {
              switch (remoteUpdate.getStatus()) {
                case REJECTED_NONFASTFORWARD:
                  log.info("Non fast forward push rejected");
                  return null;
                case OK:
                  break;
                default:
                  log.error("Unexpected push error: {}", remoteUpdate);
                  throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, remoteUpdate.toString()));
              }
            }
          }
          log.info("Commit is pushed");
        } catch (GitAPIException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, e));
        }
        updateRevisions();
        return getRevision(commitId);
      }

      @NotNull
      private String getFullPath(String name) {
        final StringBuilder fullPath = new StringBuilder();
        final Iterator<GitTreeUpdate> iter = treeStack.descendingIterator();
        while (iter.hasNext()) {
          fullPath.append(iter.next().name).append('/');
        }
        fullPath.append(name);
        return fullPath.toString();
      }
    };
  }

  public static class GitTreeUpdate {
    @NotNull
    private final String name;
    @NotNull
    private final Map<String, GitTreeEntry> entries = new TreeMap<>();

    public GitTreeUpdate(@NotNull String name) throws IOException {
      this.name = name;
    }

    public GitTreeUpdate(@NotNull String name, @NotNull ObjectReader reader, @NotNull ObjectId originalTreeId) throws IOException {
      this.name = name;
      CanonicalTreeParser treeParser = new CanonicalTreeParser(emptyBytes, reader, originalTreeId);
      while (!treeParser.eof()) {
        entries.put(treeParser.getEntryPathString(), new GitTreeEntry(
            treeParser.getEntryFileMode(),
            treeParser.getEntryObjectId()
        ));
        treeParser.next();
      }
    }

    @NotNull
    public ObjectId buildTree(@NotNull ObjectInserter inserter) throws IOException, SVNException {
      final TreeFormatter treeBuilder = new TreeFormatter();
      for (Map.Entry<String, GitTreeEntry> entry : entries.entrySet()) {
        final String name = entry.getKey();
        final GitTreeEntry value = entry.getValue();
        treeBuilder.append(name, value.fileMode, value.objectId);
      }
      return inserter.insert(treeBuilder);
    }
  }

  public static class GitTreeEntry {
    @NotNull
    private final FileMode fileMode;
    @NotNull
    private final ObjectId objectId;

    public GitTreeEntry(@NotNull FileMode fileMode, @NotNull ObjectId objectId) {
      this.fileMode = fileMode;
      this.objectId = objectId;
    }
  }
}
