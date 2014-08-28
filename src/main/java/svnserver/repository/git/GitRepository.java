package svnserver.repository.git;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.StringHelper;
import svnserver.auth.User;
import svnserver.repository.*;
import svnserver.repository.git.prop.GitAttributes;
import svnserver.repository.git.prop.GitIgnore;
import svnserver.repository.git.prop.GitProperty;
import svnserver.repository.git.prop.GitTortoise;

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
  private static final int REPORT_DELAY = 2500;

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepository.class);

  @NotNull
  public static final byte[] emptyBytes = new byte[0];
  @NotNull
  private final Repository repository;
  @NotNull
  private final List<Repository> linkedRepositories;
  @NotNull
  private final GitPushMode pushMode;
  @NotNull
  private final List<GitRevision> revisions = new ArrayList<>();
  @NotNull
  private final Map<String, IntList> lastUpdates = new ConcurrentHashMap<>();
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
  private final Map<String, String> md5Cache = new ConcurrentHashMap<>();
  @NotNull
  private final Map<ObjectId, GitProperty[]> directoryPropertyCache = new ConcurrentHashMap<>();
  @NotNull
  private final Map<ObjectId, GitProperty> filePropertyCache = new ConcurrentHashMap<>();

  public GitRepository(@NotNull Repository repository, @NotNull List<Repository> linked,
                       @NotNull GitPushMode pushMode, @NotNull String branch) throws IOException, SVNException {
    this.repository = repository;
    this.pushMode = pushMode;
    linkedRepositories = new ArrayList<>(linked);
    final Ref branchRef = repository.getRef(branch);
    if (branchRef == null) {
      throw new IOException("Branch not found: " + branch);
    }
    this.branch = branchRef.getName();
    //addRevisionInfo(getEmptyCommit(repository));
    updateRevisions();
    this.uuid = UUID.nameUUIDFromBytes((getRepositoryId() + "\0" + this.branch).getBytes(StandardCharsets.UTF_8)).toString();
    log.info("Repository ready (branch: {}, sha1: {})", this.branch, branchRef.getObjectId().getName());
  }

  @NotNull
  private String getRepositoryId() {
    if (revisions.size() > 1) {
      RevCommit commit = revisions.get(1).getCommit();
      if (commit.getParentCount() == 0) {
        return commit.getName();
      }
    }
    return revisions.get(0).getCommit().getName();
  }

  @Override
  public void updateRevisions() throws IOException, SVNException {
    // Fast check.
    lock.readLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      if (lastRevision >= 0) {
        final ObjectId lastCommitId = revisions.get(lastRevision).getCommit();
        final Ref master = repository.getRef(branch);
        if (master.getObjectId().equals(lastCommitId)) {
          return;
        }
      }
    } finally {
      lock.readLock().unlock();
    }
    // Real update.
    lock.writeLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      final ObjectId lastCommitId = lastRevision < 0 ? null : revisions.get(lastRevision).getCommit();
      final Ref master = repository.getRef(branch);
      final List<RevCommit> newRevs = new ArrayList<>();
      final RevWalk revWalk = new RevWalk(repository);
      ObjectId objectId = master.getObjectId();
      while (true) {
        if (objectId.equals(lastCommitId)) {
          break;
        }
        final RevCommit commit = revWalk.parseCommit(objectId);
        newRevs.add(commit);
        if (commit.getParentCount() == 0) break;
        objectId = commit.getParent(0);
      }
      if (!newRevs.isEmpty()) {
        if (lastRevision < 0) {
          final RevCommit firstCommit = newRevs.get(newRevs.size() - 1);
          if (!isTreeEmpty(firstCommit.getTree())) {
            newRevs.add(getEmptyCommit(repository));
          }
        }
        final long beginTime = System.currentTimeMillis();
        int processed = 0;
        long reportTime = beginTime;
        log.info("Loading revision changes: {} revision", newRevs.size());
        for (int i = newRevs.size() - 1; i >= 0; i--) {
          addRevisionInfo(newRevs.get(i));
          processed++;
          long currentTime = System.currentTimeMillis();
          if (currentTime - reportTime > REPORT_DELAY) {
            log.info("  processed revision: {} ({} rev/sec)", newRevs.size() - i, 1000.0f * processed / (currentTime - reportTime));
            reportTime = currentTime;
            processed = 0;
          }
        }
        final long endTime = System.currentTimeMillis();
        log.info("Revision changes loaded: {} ms", endTime - beginTime);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private boolean isTreeEmpty(RevTree tree) throws IOException {
    return new CanonicalTreeParser(GitRepository.emptyBytes, repository.newObjectReader(), tree).eof();
  }

  private void addRevisionInfo(@NotNull RevCommit commit) throws IOException, SVNException {
    final int revisionId = revisions.size();
    final Map<String, GitLogEntry> changes = new HashMap<>();
    final GitFile oldTree;
    if (revisions.isEmpty()) {
      oldTree = null;
    } else {
      oldTree = new GitFile(this, revisions.get(revisionId - 1).getCommit(), revisionId - 1);
    }
    final GitFile newTree = new GitFile(this, commit, revisionId);
    collectChanges(changes, "", oldTree, newTree);
    for (String path : changes.keySet()) {
      lastUpdates.compute(path, (key, list) -> {
        final IntList result = list == null ? new IntList() : list;
        result.add(revisionId);
        return result;
      });
    }
    revisions.add(new GitRevision(this, revisionId, changes, commit));
  }

  private void collectChanges(@NotNull Map<String, GitLogEntry> changes, @NotNull String path,
                              @Nullable GitFile oldTree, @NotNull GitFile newTree) throws IOException, SVNException {
    final Iterator<GitFile> oldEntries = oldTree != null ? oldTree.getEntries().iterator() : Collections.emptyIterator();
    if (path.isEmpty()) {
      final GitLogEntry logEntry = new GitLogEntry(oldTree, newTree);
      if (oldTree == null || logEntry.isContentModified() || logEntry.isPropertyModified()) {
        changes.put("/", logEntry);
      }
    }
    GitFile oldValue = oldEntries.hasNext() ? oldEntries.next() : null;
    for (GitFile newEntry : newTree.getEntries()) {
      final GitFile oldEntry;
      if (oldValue != null) {
        while (true) {
          final int compare = oldValue.getTreeEntry().compareTo(newEntry.getTreeEntry());
          if (compare == 0) {
            oldEntry = oldValue;
            oldValue = oldEntries.hasNext() ? oldEntries.next() : null;
            break;
          }
          if (compare > 0) {
            oldEntry = null;
            break;
          }
          if (!oldEntries.hasNext()) {
            oldValue = null;
            oldEntry = null;
            break;
          }
          changes.put(StringHelper.joinPath(path, oldValue.getFileName()), new GitLogEntry(oldValue, null));
          oldValue = oldEntries.next();
        }
      } else {
        oldEntry = null;
      }
      if (!newEntry.equals(oldEntry)) {
        final String fullPath = StringHelper.joinPath(path, newEntry.getFileName());
        final GitLogEntry logEntry = new GitLogEntry(oldEntry, newEntry);
        if (oldEntry == null || logEntry.isContentModified() || logEntry.isPropertyModified()) {
          final GitLogEntry oldChange = changes.put(fullPath, logEntry);
          if (oldChange != null) {
            changes.put(fullPath, new GitLogEntry(oldChange.getOldEntry(), newEntry));
          }
        }
        if (newEntry.isDirectory()) {
          collectChanges(changes, fullPath, ((oldEntry != null) && oldEntry.isDirectory()) ? oldEntry : null, newEntry);
        }
      }
    }
    while (oldEntries.hasNext()) {
      final GitFile entry = oldEntries.next();
      final String fullPath = StringHelper.joinPath(path, entry.getFileName());
      final GitLogEntry oldChange = changes.put(fullPath, new GitLogEntry(entry, null));
      if (oldChange != null) {
        changes.put(fullPath, new GitLogEntry(entry, oldChange.getNewEntry()));
      }
    }
  }

  @NotNull
  public GitProperty[] collectProperties(@NotNull GitTreeEntry treeEntry, @NotNull VcsSupplier<Iterable<GitTreeEntry>> entryProvider) throws IOException, SVNException {
    if (treeEntry.getFileMode().getObjectType() == Constants.OBJ_BLOB)
      return GitProperty.emptyArray;

    GitProperty[] props = directoryPropertyCache.get(treeEntry.getObjectId().getObject());
    if (props == null) {
      final List<GitProperty> propList = new ArrayList<>();
      for (GitTreeEntry entry : entryProvider.get()) {
        final GitProperty property = parseGitProperty(entry.getFileName(), entry.getObjectId());
        if (property != null) {
          propList.add(property);
        }
      }
      if (!propList.isEmpty()) {
        props = propList.toArray(new GitProperty[propList.size()]);
      } else {
        props = GitProperty.emptyArray;
      }
      directoryPropertyCache.put(treeEntry.getObjectId().getObject(), props);
    }
    return props;
  }

  @Nullable
  private GitProperty parseGitProperty(String fileName, GitObject<ObjectId> objectId) throws IOException, SVNException {
    switch (fileName) {
      case ".tgitconfig":
        return cachedParseGitProperty(objectId, GitTortoise::new);
      case ".gitattributes":
        return cachedParseGitProperty(objectId, GitAttributes::new);
      case ".gitignore":
        return cachedParseGitProperty(objectId, GitIgnore::new);
      default:
        return null;
    }
  }

  @Nullable
  private GitProperty cachedParseGitProperty(GitObject<ObjectId> objectId, VcsFunction<String, GitProperty> properyParser) throws IOException, SVNException {
    GitProperty property = filePropertyCache.get(objectId.getObject());
    if (property == null) {
      property = properyParser.apply(loadContent(objectId));
      filePropertyCache.put(objectId.getObject(), property);
    }
    return property;
  }

  @NotNull
  @Override
  public GitRevision getLatestRevision() throws IOException {
    lock.readLock().lock();
    try {
      return revisions.get(revisions.size() - 1);
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
  public Repository getRepository() {
    return repository;
  }

  @NotNull
  public String getObjectMD5(@NotNull GitObject<? extends ObjectId> objectId, char type, @NotNull VcsSupplier<InputStream> streamFactory) throws IOException, SVNException {
    final String key = type + objectId.getObject().name();
    String result = md5Cache.get(key);
    if (result == null) {
      final byte[] buffer = new byte[64 * 1024];
      final MessageDigest md5 = getMd5();
      try (InputStream stream = streamFactory.get()) {
        while (true) {
          int size = stream.read(buffer);
          if (size < 0) break;
          md5.update(buffer, 0, size);
        }
      }
      result = StringHelper.toHex(md5.digest());
      md5Cache.putIfAbsent(key, result);
    }
    return result;
  }

  @NotNull
  @Override
  public GitRevision getRevisionInfo(int revision) throws IOException, SVNException {
    final GitRevision revisionInfo = getRevisionInfoUnsafe(revision);
    if (revisionInfo == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revision));
    }
    return revisionInfo;
  }

  @NotNull
  public GitRevision sureRevisionInfo(int revision) throws IOException {
    final GitRevision revisionInfo = getRevisionInfoUnsafe(revision);
    if (revisionInfo == null) {
      throw new IllegalStateException("No such revision " + revision);
    }
    return revisionInfo;
  }

  @Nullable
  private GitRevision getRevisionInfoUnsafe(int revision) throws IOException {
    lock.readLock().lock();
    try {
      if (revision >= revisions.size())
        return null;
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
  private static RevCommit getEmptyCommit(@NotNull Repository repository) throws IOException {
    final ObjectInserter inserter = repository.newObjectInserter();
    final TreeFormatter treeBuilder = new TreeFormatter();
    final ObjectId treeId = inserter.insert(treeBuilder);

    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(new PersonIdent("", "", 0, 0));
    commitBuilder.setCommitter(new PersonIdent("", "", 0, 0));
    commitBuilder.setMessage("");
    commitBuilder.setTreeId(treeId);
    final ObjectId commitId = inserter.insert(commitBuilder);
    inserter.flush();

    final RevWalk revWalk = new RevWalk(repository);
    return revWalk.parseCommit(commitId);
  }

  @NotNull
  @Override
  public VcsDeltaConsumer createFile() throws IOException, SVNException {
    return new GitDeltaConsumer(this, null);
  }

  @NotNull
  @Override
  public VcsDeltaConsumer modifyFile(@NotNull VcsFile file) throws IOException, SVNException {
    return new GitDeltaConsumer(this, (GitFile) file);
  }

  public int getLastChange(@NotNull String nodePath, int beforeRevision) {
    if (nodePath.isEmpty()) return beforeRevision;
    final IntList revs = this.lastUpdates.get(nodePath);
    if (revs != null) {
      for (int i = revs.size() - 1; i >= 0; --i) {
        final int rev = revs.get(i);
        if (rev <= beforeRevision) {
          return rev;
        }
      }
    }
    throw new IllegalStateException("Internal error: can't find lastChange revision for file: " + nodePath + "@" + beforeRevision);
  }

  @NotNull
  @Override
  public VcsCommitBuilder createCommitBuilder() throws IOException, SVNException {
    return new GitCommitBuilder(repository.getRef(branch));
  }

  @NotNull
  public static String loadContent(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    final byte[] bytes = objectId.getRepo().newObjectReader().open(objectId.getObject()).getBytes();
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @NotNull
  public Iterable<GitTreeEntry> loadTree(@Nullable GitTreeEntry tree) throws IOException {
    final GitObject<ObjectId> treeId = getTreeObject(tree);
    // Loading tree.
    if (treeId == null) {
      return Collections.emptyList();
    }
    final List<GitTreeEntry> result = new ArrayList<>();
    final Repository repo = treeId.getRepo();
    final CanonicalTreeParser treeParser = new CanonicalTreeParser(GitRepository.emptyBytes, repo.newObjectReader(), treeId.getObject());
    while (!treeParser.eof()) {
      result.add(new GitTreeEntry(
          treeParser.getEntryFileMode(),
          new GitObject<>(repo, treeParser.getEntryObjectId()),
          treeParser.getEntryPathString()
      ));
      treeParser.next();
    }
    return result;
  }

  @Nullable
  private GitObject<ObjectId> getTreeObject(@Nullable GitTreeEntry tree) throws IOException {
    if (tree == null) {
      return null;
    }
    // Get tree object
    if (tree.getFileMode().equals(FileMode.TREE)) {
      return tree.getObjectId();
    }
    if (tree.getFileMode().equals(FileMode.GITLINK)) {
      GitObject<RevCommit> linkedCommit = loadLinkedCommit(tree.getObjectId().getObject());
      if (linkedCommit == null) {
        return null;
      }
      return new GitObject<>(linkedCommit.getRepo(), linkedCommit.getObject().getTree());
    } else {
      return null;
    }
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

  private class GitPropertyValidator {
    @NotNull
    private final Deque<GitFile> treeStack;

    public GitPropertyValidator(@NotNull GitFile root) {
      this.treeStack = new ArrayDeque<>();
      this.treeStack.push(root);
    }

    public void openDir(@NotNull String name) throws IOException, SVNException {
      final GitFile file = treeStack.element().getEntry(name);
      if (file == null) {
        throw new IllegalStateException("Invalid state: can't find file " + name + " in created commit.");
      }
      treeStack.push(file);
    }

    public void checkProperties(@Nullable String name, @NotNull Map<String, String> properties) throws IOException, SVNException {
      final GitFile dir = treeStack.element();
      final GitFile node = name == null ? dir : dir.getEntry(name);
      if (node == null) {
        throw new IllegalStateException("Invalid state: can't find entry " + name + " in created commit.");
      }
      final Map<String, String> expected = node.getProperties(false);
      if (!properties.equals(expected)) {
        final StringBuilder message = new StringBuilder();
        message.append("Can't commit entry: ").append(node.getFullPath()).append("\nInvalid svn properties found.\n");
        message.append("Expected:\n");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
          message.append("  ").append(entry.getKey()).append(" = \"").append(entry.getValue()).append("\"\n");
        }
        message.append("Actual:\n");
        for (Map.Entry<String, String> entry : properties.entrySet()) {
          message.append("  ").append(entry.getKey()).append(" = \"").append(entry.getValue()).append("\"\n");
        }
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.REPOS_HOOK_FAILURE, message.toString()));
      }
    }

    public void closeDir() {
      treeStack.pop();
    }
  }

  private class GitCommitBuilder implements VcsCommitBuilder {
    @NotNull
    private final Deque<GitTreeUpdate> treeStack;
    @NotNull
    private final ObjectInserter inserter;
    @NotNull
    private final GitRevision revision;
    @NotNull
    private final Ref branchRef;
    @NotNull
    private final List<VcsConsumer<GitPropertyValidator>> validateActions = new ArrayList<>();

    public GitCommitBuilder(@NotNull Ref branchRef) throws IOException, SVNException {
      this.inserter = repository.newObjectInserter();
      this.branchRef = branchRef;
      this.revision = getLatestRevision();
      this.treeStack = new ArrayDeque<>();
      this.treeStack.push(new GitTreeUpdate("", loadTree(new GitTreeEntry(repository, FileMode.TREE, revision.getCommit().getTree(), ""))));
    }

    @Override
    public void checkUpToDate(@NotNull String path, int rev) throws SVNException, IOException {
      final GitFile file = revision.getFile(path);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, path));
      } else if (file.getLastChange().getId() > rev) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "Working copy is not up-to-date: " + path));
      }
    }

    @Override
    public void addDir(@NotNull String name, @Nullable VcsFile sourceDir) throws SVNException, IOException {
      final GitTreeUpdate current = treeStack.element();
      if (current.getEntries().containsKey(name)) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, getFullPath(name)));
      }
      final GitFile source = (GitFile) sourceDir;
      validateActions.add(validator -> validator.openDir(name));
      treeStack.push(new GitTreeUpdate(name, loadTree(source == null ? null : source.getTreeEntry())));
    }

    @Override
    public void openDir(@NotNull String name) throws SVNException, IOException {
      final GitTreeUpdate current = treeStack.element();
      // todo: ???
      final GitTreeEntry originalDir = current.getEntries().remove(name);
      if ((originalDir == null) || (!originalDir.getFileMode().equals(FileMode.TREE))) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
      }
      validateActions.add(validator -> validator.openDir(name));
      treeStack.push(new GitTreeUpdate(name, loadTree(originalDir)));
    }

    @Override
    public void checkDirProperties(@NotNull Map<String, String> props) throws SVNException, IOException {
      validateActions.add(validator -> validator.checkProperties(null, props));
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
      if (current.getEntries().put(last.getName(), new GitTreeEntry(FileMode.TREE, new GitObject<>(repository, subtreeId), last.getName())) != null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, fullPath));
      }
      validateActions.add(GitPropertyValidator::closeDir);
    }

    @Override
    public void saveFile(@NotNull String name, @NotNull VcsDeltaConsumer deltaConsumer, boolean modify) throws SVNException, IOException {
      final GitDeltaConsumer gitDeltaConsumer = (GitDeltaConsumer) deltaConsumer;
      final GitTreeUpdate current = treeStack.element();
      final GitTreeEntry entry = current.getEntries().get(name);
      final GitObject<ObjectId> originalId = gitDeltaConsumer.getOriginalId();
      if (modify ^ (entry != null)) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "Working copy is not up-to-date: " + getFullPath(name)));
      }
      final GitObject<ObjectId> objectId = gitDeltaConsumer.getObjectId();
      if (objectId == null) {
        // Content not updated.
        if (originalId == null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Added file without content: " + getFullPath(name)));
        }
        return;
      }
      current.getEntries().put(name, new GitTreeEntry(getFileMode(gitDeltaConsumer.getProperties()), objectId, name));
      validateActions.add(validator -> validator.checkProperties(name, gitDeltaConsumer.getProperties()));
    }

    private FileMode getFileMode(@NotNull Map<String, String> props) {
      if (props.containsKey(SVNProperty.SPECIAL)) return FileMode.SYMLINK;
      if (props.containsKey(SVNProperty.EXECUTABLE)) return FileMode.EXECUTABLE_FILE;
      return FileMode.REGULAR_FILE;
    }

    @Override
    public void delete(@NotNull String name) throws SVNException, IOException {
      final GitTreeUpdate current = treeStack.element();
      final GitTreeEntry entry = current.getEntries().remove(name);
      if (entry == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
      }
    }

    @Override
    public GitRevision commit(@NotNull User userInfo, @NotNull String message) throws SVNException, IOException {
      synchronized (pushLock) {
        final GitTreeUpdate root = treeStack.element();
        final ObjectId treeId = root.buildTree(inserter);
        log.info("Create tree {} for commit.", treeId.name());
        inserter.flush();

        final CommitBuilder commitBuilder = new CommitBuilder();
        final PersonIdent ident = createIdent(userInfo);
        commitBuilder.setAuthor(ident);
        commitBuilder.setCommitter(ident);
        commitBuilder.setMessage(message);
        commitBuilder.setParentId(revision.getCommit().getId());
        commitBuilder.setTreeId(treeId);
        final ObjectId commitId = inserter.insert(commitBuilder);
        inserter.flush();

        log.info("Validate properties");
        validateProperties(new RevWalk(repository).parseCommit(commitId));

        log.info("Create commit {}: {}", commitId.name(), message);
        log.info("Try to push commit in branch: {}", branchRef.getName());
        if (!pushMode.push(repository, commitId, branchRef)) {
          log.info("Non fast forward push rejected");
          return null;
        }
        log.info("Commit is pushed");

        updateRevisions();
        return getRevision(commitId);
      }
    }

    private void validateProperties(@NotNull RevCommit commit) throws IOException, SVNException {
      final GitFile root = new GitFile(GitRepository.this, commit, 0);
      final GitPropertyValidator validator = new GitPropertyValidator(root);
      for (VcsConsumer<GitPropertyValidator> validateAction : validateActions) {
        validateAction.accept(validator);
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
  }
}
