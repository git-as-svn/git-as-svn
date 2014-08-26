package svnserver.repository.git;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
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
  public interface StreamFactory {

    InputStream openStream() throws IOException;
  }

  private static final int REPORT_DELAY = 1000;

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
  private final Map<String, String> cacheMd5 = new ConcurrentHashMap<>();
  @NotNull
  private final Map<String, GitProperty[]> cacheProperties = new ConcurrentHashMap<>();

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
    addRevisionInfo(getEmptyCommit(repository));
    updateRevisions();
    this.uuid = UUID.nameUUIDFromBytes((getRevisionInfo(1).getCommit().getName() + "\0" + this.branch).getBytes(StandardCharsets.UTF_8)).toString();
    log.info("Repository ready (branch: {})", this.branch);
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
      if (!newRevs.isEmpty()) {
        final long beginTime = System.currentTimeMillis();
        long reportTime = beginTime;
        log.info("Loading revision changes: {} revision", newRevs.size());
        for (int i = newRevs.size() - 1; i >= 0; i--) {
          addRevisionInfo(newRevs.get(i));
          long currentTime = System.currentTimeMillis();
          if (currentTime - reportTime > REPORT_DELAY) {
            final int processed = newRevs.size() - i;
            log.info("  processed revision: {} ({} ms/rev)", processed, (currentTime - beginTime) / processed);
            reportTime = currentTime;
          }
        }
        final long endTime = System.currentTimeMillis();
        log.info("Revision changes loaded: {} ms", endTime - beginTime);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void addRevisionInfo(@NotNull RevCommit commit) throws IOException {
    final int revisionId = revisions.size();
    final TreeMap<String, GitLogEntry> changes = new TreeMap<>();
    final GitFile oldTree;
    if (revisions.isEmpty()) {
      oldTree = null;
    } else {
      oldTree = new GitFile(this, new GitTreeEntry(repository, FileMode.TREE, revisions.get(revisionId - 1).getCommit().getTree()), "", GitProperty.emptyArray, revisionId - 1, this::getProperties);
    }
    final GitFile newTree = new GitFile(this, new GitTreeEntry(repository, FileMode.TREE, commit.getTree()), "", GitProperty.emptyArray, revisionId, this::collectProperties);
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
                              @Nullable GitFile oldTree,
                              @NotNull GitFile newTree) throws IOException {
    final Map<String, VcsFile> oldEntries = oldTree != null ? oldTree.getEntries() : Collections.emptyMap();
    final Map<String, VcsFile> newEntries = newTree.getEntries();
    if (path.isEmpty()) {
      final GitLogEntry logEntry = new GitLogEntry(oldTree, newTree);
      if (oldTree == null || logEntry.isContentModified() || logEntry.isPropertyModified()) {
        changes.put("/", logEntry);
      }
    }
    for (Map.Entry<String, VcsFile> entry : newEntries.entrySet()) {
      final String name = entry.getKey();
      final GitFile newEntry = (GitFile) entry.getValue();
      final GitFile oldEntry = (GitFile) oldEntries.remove(name);
      if (!newEntry.equals(oldEntry)) {
        final String fullPath = StringHelper.joinPath(path, name);
        final GitLogEntry logEntry = new GitLogEntry(oldEntry, newEntry);
        if (oldEntry == null || logEntry.isContentModified() || logEntry.isPropertyModified()) {
          changes.put(fullPath, logEntry);
        }
        if (newEntry.isDirectory()) {
          collectChanges(changes, fullPath, ((oldEntry != null) && oldEntry.isDirectory()) ? oldEntry : null, newEntry);
        }
      }
    }
    for (Map.Entry<String, VcsFile> entry : oldEntries.entrySet()) {
      final GitFile oldEntry = (GitFile) entry.getValue();
      changes.put(StringHelper.joinPath(path, entry.getKey()), new GitLogEntry(oldEntry, null));
    }
  }

  @NotNull
  private GitProperty[] collectProperties(@NotNull GitTreeEntry treeEntry) throws IOException {
    GitProperty[] props = cacheProperties.get(treeEntry.getId());
    if (props == null) {
      final List<GitProperty> propList = new ArrayList<>();
      for (Map.Entry<String, GitTreeEntry> entry : loadTree(treeEntry).entrySet()) {
        final GitProperty property = parseGitProperty(entry.getKey(), entry.getValue().getObjectId());
        if (property != null) {
          propList.add(property);
        }
      }
      if (!propList.isEmpty()) {
        props = propList.toArray(new GitProperty[propList.size()]);
        cacheProperties.put(treeEntry.getId(), props);
      } else {
        props = GitProperty.emptyArray;
      }
    }
    return props;
  }

  @Nullable
  private GitProperty parseGitProperty(String fileName, GitObject<ObjectId> objectId) throws IOException {
    switch (fileName) {
      case ".tgitconfig":
        return new GitTortoise(loadContent(objectId));
      case ".gitattributes":
        return new GitAttributes(loadContent(objectId));
      case ".gitignore":
        return new GitIgnore(loadContent(objectId));
      default:
        return null;
    }
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
  public String getObjectMD5(@NotNull GitObject<? extends ObjectId> objectId, char type, @NotNull StreamFactory streamFactory) throws IOException {
    final String key = type + objectId.getObject().name();
    String result = cacheMd5.get(key);
    if (result == null) {
      final byte[] buffer = new byte[64 * 1024];
      final MessageDigest md5 = getMd5();
      try (InputStream stream = streamFactory.openStream()) {
        while (true) {
          int size = stream.read(buffer);
          if (size < 0) break;
          md5.update(buffer, 0, size);
        }
      }
      result = StringHelper.toHex(md5.digest());
      cacheMd5.putIfAbsent(key, result);
    }
    return result;
  }

  @NotNull
  public GitProperty[] getProperties(@NotNull GitTreeEntry treeEntry) throws IOException {
    return cacheProperties.getOrDefault(treeEntry.getId(), GitProperty.emptyArray);
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
  public Map<String, GitTreeEntry> loadTree(@Nullable GitTreeEntry tree) throws IOException {
    final GitObject<ObjectId> treeId = getTreeObject(tree);
    // Loading tree.
    if (treeId == null) {
      return Collections.emptyMap();
    }
    final Map<String, GitTreeEntry> result = new TreeMap<>();
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

    public void openDir(@NotNull String name) throws IOException {
      final Map<String, VcsFile> entries = treeStack.element().getEntries();
      final GitFile file = (GitFile) entries.get(name);
      if (file == null) {
        throw new IllegalStateException("Invalid state: can't find file " + name + " in created commit.");
      }
      treeStack.push(file);
    }

    public void checkProperties(@Nullable String name, @NotNull Map<String, String> properties) throws IOException, SVNException {
      final GitFile dir = treeStack.element();
      final GitFile node = name == null ? dir : (GitFile) dir.getEntries().get(name);
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
      this.treeStack.push(new GitTreeUpdate("", loadTree(new GitTreeEntry(repository, FileMode.TREE, revision.getCommit().getTree()))));
    }

    @Override
    public void checkUpToDate(@NotNull String path, int rev) throws SVNException, IOException {
      final GitFile file = revision.getFile(path);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, path));
      } else if (file.getLastChange().getId() > rev) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, path));
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
      if (current.getEntries().put(last.getName(), new GitTreeEntry(FileMode.TREE, new GitObject<>(repository, subtreeId))) != null) {
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
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, getFullPath(name)));
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
      validateActions.add(validator -> validator.checkProperties(name, gitDeltaConsumer.getProperties()));
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
        validateProperties(treeId);

        final CommitBuilder commitBuilder = new CommitBuilder();
        final PersonIdent ident = createIdent(userInfo);
        commitBuilder.setAuthor(ident);
        commitBuilder.setCommitter(ident);
        commitBuilder.setMessage(message);
        commitBuilder.setParentId(revision.getCommit().getId());
        commitBuilder.setTreeId(treeId);
        final ObjectId commitId = inserter.insert(commitBuilder);
        inserter.flush();

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

    private void validateProperties(@NotNull ObjectId commitTreeId) throws IOException, SVNException {
      final GitRepository repo = GitRepository.this;
      final GitFile root = new GitFile(repo, new GitTreeEntry(repository, FileMode.TREE, commitTreeId), "", GitProperty.emptyArray, 0, repo::collectProperties);
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
