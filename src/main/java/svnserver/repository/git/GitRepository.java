package svnserver.repository.git;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.auth.User;
import svnserver.config.RepositoryConfig;
import svnserver.repository.VcsCommitBuilder;
import svnserver.repository.VcsDeltaConsumer;
import svnserver.repository.VcsFile;
import svnserver.repository.VcsRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
  private final List<FileRepository> linkedRepositories = new ArrayList<>();
  @NotNull
  private final List<GitRevision> revisions = new ArrayList<>();
  @NotNull
  private final Map<String, int[]> lastUpdates = new ConcurrentHashMap<>();
  @NotNull
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  // Lock for prevent concurrent pushes.
  @NotNull
  private final Object pushLock = new Object();
  @NotNull
  private final String uuid;
  @NotNull
  private final String branch;
  @NotNull
  private final Map<String, String> cacheMd5 = new ConcurrentHashMap<>();

  public GitRepository(@NotNull RepositoryConfig config) throws IOException, SVNException {
    this.repository = new FileRepository(new File(config.getPath()).getAbsoluteFile());
    log.info("Repository path: {}", repository.getDirectory());
    if (!repository.getDirectory().exists()) {
      throw new FileNotFoundException(repository.getDirectory().getPath());
    }
    for (String linkedPath : config.getLinked()) {
      FileRepository linkedRepository = new FileRepository(new File(linkedPath));
      linkedRepositories.add(linkedRepository);
    }
    final Ref branchRef = repository.getRef(config.getBranch());
    if (branchRef == null) {
      throw new IOException("Branch not found: " + config.getBranch());
    }
    this.branch = branchRef.getName();
    addRevisionInfo(getEmptyCommit(repository));
    updateRevisions();
    this.uuid = UUID.nameUUIDFromBytes((getRevisionInfo(1).getCommit().getName() + "\0" + branch).getBytes(StandardCharsets.UTF_8)).toString();
    log.info("Repository ready (branch: {})", branch);
  }

  @Override
  public void updateRevisions() throws IOException {
    // Fast check.
    lock.readLock().lock();
    try {
      final ObjectId lastCommitId = revisions.get(revisions.size() - 1).getCommit();
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
      final ObjectId lastCommitId = revisions.get(revisions.size() - 1).getCommit();
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
        addRevisionInfo(newRevs.get(i));
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void addRevisionInfo(@NotNull RevCommit commit) throws IOException {
    final int revisionId = revisions.size();
    final ObjectId oldTree = revisions.isEmpty() ? null : revisions.get(revisionId - 1).getCommit().getTree();
    final ObjectId newTree = commit.getTree();
    final TreeMap<String, GitLogEntry> changes = new TreeMap<>();
    collectChanges(changes, "", oldTree != null ? new GitObject<>(repository, oldTree) : null, new GitObject<>(repository, newTree));
    for (String path : changes.keySet()) {
      int[] oldRevisions = lastUpdates.get(path);
      int[] newRevisions = oldRevisions == null ? new int[1] : Arrays.copyOf(oldRevisions, oldRevisions.length + 1);
      newRevisions[newRevisions.length - 1] = revisionId;
      lastUpdates.put(path, newRevisions);
    }
    revisions.add(new GitRevision(this, revisionId, changes, commit));
  }

  private void collectChanges(@NotNull Map<String, GitLogEntry> changes, @NotNull String path,
                              @Nullable GitObject<ObjectId> oldTree,
                              @NotNull GitObject<ObjectId> newTree) throws IOException {
    final Map<String, GitTreeEntry> oldEntries = loadTree(oldTree);
    for (Map.Entry<String, GitTreeEntry> entry : loadTree(newTree).entrySet()) {
      final String name = entry.getKey();
      final GitTreeEntry newEntry = entry.getValue();
      final GitTreeEntry oldEntry = oldEntries.remove(name);
      if (!newEntry.equals(oldEntry)) {
        final String fullPath = StringHelper.joinPath(path, name);
        changes.put(fullPath, new GitLogEntry(oldEntry, newEntry));
        final GitObject<ObjectId> newTreeId = newEntry.getTreeId(this);
        if (newTreeId != null) {
          collectChanges(changes, fullPath, oldEntry == null ? null : oldEntry.getTreeId(this), newTreeId);
        }
      }
    }
    for (Map.Entry<String, GitTreeEntry> entry : oldEntries.entrySet()) {
      changes.put(StringHelper.joinPath(path, entry.getKey()), new GitLogEntry(entry.getValue(), null));
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
  public String getObjectMD5(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    String result = cacheMd5.get(objectId.getObject().name());
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
      cacheMd5.putIfAbsent(objectId.getObject().name(), result);
    }
    return result;
  }

  @NotNull
  public ObjectLoader openObject(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    return objectId.getRepo().newObjectReader().open(objectId.getObject());
  }

  @NotNull
  @Override
  public GitRevision getRevisionInfo(int revision) throws IOException, SVNException {
    lock.readLock().lock();
    try {
      if (revision >= revisions.size())
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revision));
      return revisions.get(revision);
    } finally {
      lock.readLock().unlock();
    }
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
        GitRevision revision = revisions.get(i);
        if (revision.getCommit().equals(revisionId)) {
          return revision;
        }
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revisionId.name()));
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
    return new GitDeltaConsumer(this, null, fullPath, false);
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
    return new GitDeltaConsumer(this, file, fullPath, true);
  }

  @NotNull
  @Override
  public VcsDeltaConsumer copyFile(@NotNull String fullPath, int revision) throws IOException, SVNException {
    final GitFile file = getRevisionInfo(revision).getFile(fullPath);
    if (file == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, fullPath));
    }
    return new GitDeltaConsumer(this, file, fullPath, false);
  }

  @NotNull
  @Override
  public VcsFile deleteEntry(@NotNull String fullPath, int revision) throws IOException, SVNException {
    final GitFile file = getRevisionInfo(getLatestRevision()).getFile(fullPath);
    if (file == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, fullPath));
    }
    if (file.getLastChange().getId() > revision) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "File is not up-to-date: " + fullPath));
    }
    return file;
  }

  public int getLastChange(@NotNull String nodePath, int revision) {
    if (nodePath.isEmpty()) return revision;
    int[] revs = this.lastUpdates.get(nodePath);
    if (revs != null) {
      for (int i = revs.length - 1; i >= 0; --i) {
        if (revs[i] <= revision) {
          return revs[i];
        }
      }
    }
    throw new IllegalStateException("Internal error: can't find lastChange revision for file: " + nodePath + "@" + revision);
  }

  @NotNull
  @Override
  public VcsCommitBuilder createCommitBuilder() throws IOException {
    final Ref branchRef = repository.getRef(branch);
    final ObjectId objectId = branchRef.getObjectId();
    final RevWalk revWalk = new RevWalk(repository);
    final ObjectInserter inserter = repository.newObjectInserter();
    final RevCommit commit = revWalk.parseCommit(objectId);

    final Deque<GitTreeUpdate> treeStack = new ArrayDeque<>();
    treeStack.push(new GitTreeUpdate("", loadTree(new GitObject<>(repository, commit.getTree()))));

    return new VcsCommitBuilder() {
      @Override
      public void addDir(@NotNull String name, @Nullable String originalName, int originalRev) throws SVNException, IOException {
        final GitTreeUpdate current = treeStack.element();
        if (current.getEntries().containsKey(name)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, getFullPath(name)));
        }
        final GitObject<? extends ObjectId> srcId;
        if (originalName == null) {
          srcId = null;
        } else {
          final GitFile file = getRevisionInfo(originalRev).getFile(originalName);
          if ((file == null) || (!file.isDirectory())) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, originalName));
          }
          srcId = file.getObjectId();
        }
        treeStack.push(new GitTreeUpdate(name, loadTree(srcId)));
      }

      @Override
      public void openDir(@NotNull String name) throws SVNException, IOException {
        final GitTreeUpdate current = treeStack.element();
        final GitTreeEntry originalDir = current.getEntries().remove(name);
        if ((originalDir == null) || (!originalDir.getFileMode().equals(FileMode.TREE))) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
        }
        treeStack.push(new GitTreeUpdate(name, loadTree(originalDir.getObjectId())));
      }

      @Override
      public void closeDir() throws SVNException, IOException {
        final GitTreeUpdate last = treeStack.pop();
        final GitTreeUpdate current = treeStack.element();
        final String fullPath = getFullPath(last.getName());
        if (last.getEntries().isEmpty()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Empty directories is not supported: " + fullPath));
        }
        final ObjectId subtreeId = last.buildTree(inserter);
        log.info("Create tree {} for dir: {}", subtreeId.name(), fullPath);
        if (current.getEntries().put(last.getName(), new GitTreeEntry(FileMode.TREE, new GitObject<>(repository, subtreeId))) != null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, fullPath));
        }
      }

      @Override
      public void saveFile(@NotNull String name, @NotNull VcsDeltaConsumer deltaConsumer) throws SVNException {
        final GitDeltaConsumer gitDeltaConsumer = (GitDeltaConsumer) deltaConsumer;
        final GitTreeUpdate current = treeStack.element();
        final GitTreeEntry entry = current.getEntries().get(name);
        final GitObject<ObjectId> originalId = gitDeltaConsumer.getOriginalId();
        if ((gitDeltaConsumer.isUpdate()) && (entry == null)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
        } else if ((gitDeltaConsumer.isUpdate()) && (!entry.getObjectId().equals(originalId))) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, getFullPath(name)));
        } else if ((!gitDeltaConsumer.isUpdate()) && (entry != null)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, getFullPath(name)));
        }
        final GitObject<ObjectId> objectId = gitDeltaConsumer.getObjectId();
        if (objectId == null) {
          // Content not updated.
          if (originalId == null) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, getFullPath(name)));
          }
          return;
        }
        current.getEntries().put(name, new GitTreeEntry(gitDeltaConsumer.getFileMode(), objectId));
      }

      @Override
      public void delete(@NotNull String name, @NotNull VcsFile file) throws SVNException, IOException {
        final GitTreeUpdate current = treeStack.element();
        final GitFile gitFile = (GitFile) file;
        final GitTreeEntry entry = current.getEntries().remove(name);
        if (entry == null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
        }
        if (!gitFile.getObjectId().equals(entry.getObjectId())) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, getFullPath(name)));
        }
      }

      @Override
      public GitRevision commit(@NotNull User userInfo, @NotNull String message) throws SVNException, IOException {
        synchronized (pushLock) {
          final GitTreeUpdate root = treeStack.element();
          final ObjectId treeId = root.buildTree(inserter);
          log.info("Create tree {} for commit.", treeId.name());

          final CommitBuilder commitBuilder = new CommitBuilder();
          final PersonIdent ident = createIdent(userInfo);
          commitBuilder.setAuthor(ident);
          commitBuilder.setCommitter(ident);
          commitBuilder.setMessage(message);
          commitBuilder.setParentId(commit.getId());
          commitBuilder.setTreeId(treeId);
          final ObjectId commitId = inserter.insert(commitBuilder);

          log.info("Create commit {}: {}", commitId.name(), message);
          log.info("Try to push commit in branch: {}", branchRef.getName());
          if (!GitHelper.pushNative(repository, commitId, branchRef)) {
            log.info("Non fast forward push rejected");
            return null;
          }
          log.info("Commit is pushed");

          updateRevisions();
          return getRevision(commitId);
        }
      }

      private PersonIdent createIdent(User userInfo) {
        final String realName = userInfo.getRealName();
        final String email = userInfo.getEmail();
        return new PersonIdent(realName, email == null ? "" : email);
      }

      @NotNull
      private String getFullPath(String name) {
        final StringBuilder fullPath = new StringBuilder();
        final Iterator<GitTreeUpdate> iter = treeStack.descendingIterator();
        while (iter.hasNext()) {
          fullPath.append(iter.next().getName()).append('/');
        }
        fullPath.append(name);
        return fullPath.toString();
      }
    };
  }

  @NotNull
  public static Map<String, GitTreeEntry> loadTree(@Nullable GitObject<? extends ObjectId> treeId) throws IOException {
    Map<String, GitTreeEntry> result = new TreeMap<>();
    if (treeId != null) {
      final Repository repo = treeId.getRepo();
      final CanonicalTreeParser treeParser = new CanonicalTreeParser(GitRepository.emptyBytes, repo.newObjectReader(), treeId.getObject());
      while (!treeParser.eof()) {
        final GitTreeEntry treeEntry = new GitTreeEntry(
            treeParser.getEntryFileMode(),
            new GitObject<>(repo, treeParser.getEntryObjectId())
        );
        result.put(treeParser.getEntryPathString(), treeEntry);
        treeParser.next();
      }
    }
    return result;
  }

  @Nullable
  public GitObject<RevCommit> loadLinkedCommit(@NotNull ObjectId objectId) throws IOException {
    for (Repository repo : linkedRepositories) {
      if (repo.hasObject(objectId)) {
        final RevWalk revWalk = new RevWalk(repo);
        return new GitObject<>(repo, revWalk.parseCommit(objectId));

      }
    }
    return null;
  }
}
