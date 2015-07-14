/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import com.sun.nio.sctp.InvalidStreamException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapdb.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import svnserver.StringHelper;
import svnserver.repository.*;
import svnserver.repository.git.cache.CacheChange;
import svnserver.repository.git.cache.CacheRevision;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.filter.GitFilterHelper;
import svnserver.repository.git.filter.GitFilterLink;
import svnserver.repository.git.filter.GitFilterRaw;
import svnserver.repository.git.prop.GitProperty;
import svnserver.repository.git.prop.GitPropertyFactory;
import svnserver.repository.git.prop.PropertyMapping;
import svnserver.repository.locks.LockManagerFactory;
import svnserver.repository.locks.LockManagerRead;
import svnserver.repository.locks.LockManagerWrite;
import svnserver.repository.locks.LockWorker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

/**
 * Implementation for Git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitRepository implements VcsRepository {
  private static final int REPORT_DELAY = 2500;
  private static final int MARK_NO_FILE = -1;

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepository.class);
  @NotNull
  private final LockManagerFactory lockManagerFactory;
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
  private final TreeMap<Long, GitRevision> revisionByDate = new TreeMap<>();
  @NotNull
  private final TreeMap<ObjectId, GitRevision> revisionByHash = new TreeMap<>();
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
  private final String gitBranch;
  @NotNull
  private final String svnBranch;
  @NotNull
  private final DB cacheDb;
  @NotNull
  private final Map<String, Boolean> binaryCache;
  @NotNull
  private final Map<String, GitFilter> gitFilters;
  @NotNull
  private final Map<ObjectId, GitProperty[]> directoryPropertyCache = new ConcurrentHashMap<>();
  @NotNull
  private final Map<ObjectId, GitProperty[]> filePropertyCache = new ConcurrentHashMap<>();
  private final boolean renameDetection;

  public GitRepository(@NotNull Repository repository,
                       @NotNull List<Repository> linked,
                       @NotNull GitPushMode pushMode,
                       @NotNull String branch,
                       boolean renameDetection,
                       @NotNull LockManagerFactory lockManagerFactory,
                       @NotNull DB cacheDb) throws IOException, SVNException {
    this.cacheDb = cacheDb;
    this.binaryCache = cacheDb.getHashMap("cache.binary");
    this.repository = repository;
    this.pushMode = pushMode;
    this.renameDetection = renameDetection;
    this.lockManagerFactory = lockManagerFactory;
    this.gitFilters = GitFilterHelper.createFilters(cacheDb);
    linkedRepositories = new ArrayList<>(linked);

    this.svnBranch = LayoutHelper.initRepository(repository, branch).getName();
    this.gitBranch = Constants.R_HEADS + branch;
    final String repositoryId = loadRepositoryId(repository, svnBranch);
    this.uuid = UUID.nameUUIDFromBytes((repositoryId + "\0" + gitBranch).getBytes(StandardCharsets.UTF_8)).toString();

    log.info("Repository registered (branch: {})", gitBranch);
  }

  @NotNull
  private static String loadRepositoryId(@NotNull Repository repository, @NotNull String refName) throws IOException {
    final Ref ref = repository.getRef(refName);
    if (ref == null) {
      throw new IllegalStateException();
    }

    ObjectId oid = ref.getObjectId();
    final RevWalk revWalk = new RevWalk(repository);
    while (true) {
      final RevCommit revCommit = revWalk.parseCommit(oid);
      if (revCommit.getParentCount() == 0) {
        return LayoutHelper.loadRepositoryId(repository.newObjectReader(), oid);
      }
      oid = revCommit.getParent(0);
    }
  }

  /**
   * Load all cached revisions.
   *
   * @throws IOException
   * @throws SVNException
   */
  public boolean loadRevisions() throws IOException, SVNException {
    // Fast check.
    lock.readLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      final ObjectId lastCommitId;
      if (lastRevision >= 0) {
        lastCommitId = revisions.get(lastRevision).getGitNewCommit();
        final Ref head = repository.getRef(svnBranch);
        if (head.getObjectId().equals(lastCommitId)) {
          return false;
        }
      }
    } finally {
      lock.readLock().unlock();
    }
    // Real loading.
    lock.writeLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      final ObjectId lastCommitId = lastRevision < 0 ? null : revisions.get(lastRevision).getCacheCommit();
      final Ref head = repository.getRef(svnBranch);
      final List<RevCommit> newRevs = new ArrayList<>();
      final RevWalk revWalk = new RevWalk(repository);
      ObjectId objectId = head.getObjectId();
      while (true) {
        if (objectId.equals(lastCommitId)) {
          break;
        }
        final RevCommit commit = revWalk.parseCommit(objectId);
        newRevs.add(commit);
        if (commit.getParentCount() == 0) break;
        objectId = commit.getParent(0);
      }
      if (newRevs.isEmpty()) {
        return false;
      }
      final long beginTime = System.currentTimeMillis();
      int processed = 0;
      long reportTime = beginTime;
      log.info("Loading cached revision changes: {} revision", newRevs.size());
      for (int i = newRevs.size() - 1; i >= 0; i--) {
        loadRevisionInfo(newRevs.get(i));
        processed++;
        long currentTime = System.currentTimeMillis();
        if (currentTime - reportTime > REPORT_DELAY) {
          log.info("  processed cached revision: {} ({} rev/sec)", newRevs.size() - i, 1000.0f * processed / (currentTime - reportTime));
          reportTime = currentTime;
          processed = 0;
        }
      }
      final long endTime = System.currentTimeMillis();
      log.info("Cached revision loaded: {} ms", endTime - beginTime);
      return true;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private static class CacheInfo {
    private final int id;
    @NotNull
    private RevCommit commit;
    @NotNull
    private List<CacheInfo> childs = new ArrayList<>();
    @NotNull
    private List<CacheInfo> parents = new ArrayList<>();
    @Nullable
    private String svnBranch;

    private CacheInfo(int id, @NotNull RevCommit commit) {
      this.id = id;
      this.commit = commit;
    }
  }

  /**
   * Create cache for new revisions.
   *
   * @throws IOException
   * @throws SVNException
   */
  public boolean cacheRevisions() throws IOException, SVNException {
    // Fast check.
    lock.readLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      if (lastRevision >= 0) {
        final ObjectId lastCommitId = revisions.get(lastRevision).getGitNewCommit();
        final Ref master = repository.getRef(gitBranch);
        if ((master == null) || (master.getObjectId().equals(lastCommitId))) {
          return false;
        }
      }
    } finally {
      lock.readLock().unlock();
    }
    // Real update.
    final ObjectInserter inserter = repository.newObjectInserter();
    lock.writeLock().lock();
    try {
      final Ref master = repository.getRef(gitBranch);
      final List<RevCommit> newRevs = new ArrayList<>();
      final RevWalk revWalk = new RevWalk(repository);
      ObjectId objectId = master.getObjectId();
      while (true) {
        if (revisionByHash.containsKey(objectId)) {
          break;
        }
        final RevCommit commit = revWalk.parseCommit(objectId);
        newRevs.add(commit);
        if (commit.getParentCount() == 0) break;
        objectId = commit.getParent(0);
      }
      if (!newRevs.isEmpty()) {
        final long beginTime = System.currentTimeMillis();
        int processed = 0;
        long reportTime = beginTime;
        log.info("Loading revision changes: {} revision", newRevs.size());
        int revisionId = revisions.size();
        ObjectId cacheId = revisions.get(revisions.size() - 1).getCacheCommit();
        for (int i = newRevs.size() - 1; i >= 0; i--) {
          final RevCommit revCommit = newRevs.get(i);
          final CacheRevision cacheRevision = createCache(revCommit.getParentCount() > 0 ? revWalk.parseCommit(revCommit.getParent(0)) : null, revCommit, Collections.emptyMap(), revisionId);
          cacheId = LayoutHelper.createCacheCommit(inserter, cacheId, revCommit, cacheRevision);
          inserter.flush();

          processed++;
          long currentTime = System.currentTimeMillis();
          if (currentTime - reportTime > REPORT_DELAY) {
            log.info("  processed revision: {} ({} rev/sec)", newRevs.size() - i, 1000.0f * processed / (currentTime - reportTime));
            reportTime = currentTime;
            processed = 0;

            final RefUpdate refUpdate = repository.updateRef(svnBranch);
            refUpdate.setNewObjectId(cacheId);
            refUpdate.update();
          }
          revisionId++;
        }
        final long endTime = System.currentTimeMillis();
        log.info("Revision changes loaded: {} ms", endTime - beginTime);

        final RefUpdate refUpdate = repository.updateRef(svnBranch);
        refUpdate.setNewObjectId(cacheId);
        refUpdate.update();
      }
      return !newRevs.isEmpty();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private CacheRevision createCache(@Nullable RevCommit oldCommit, @NotNull RevCommit newCommit, @NotNull Map<String, RevCommit> branches, int revisionId) throws IOException, SVNException {
    final GitFile oldTree = oldCommit == null ? new GitFileEmptyTree(this, "", revisionId - 1) : GitFileTreeEntry.create(this, oldCommit.getTree(), revisionId - 1);
    final GitFile newTree = GitFileTreeEntry.create(this, newCommit.getTree(), revisionId);
    final Map<String, CacheChange> fileChange = new TreeMap<>();
    for (Map.Entry<String, GitLogPair> entry : ChangeHelper.collectChanges(oldTree, newTree, true).entrySet()) {
      fileChange.put(entry.getKey(), new CacheChange(entry.getValue()));
    }
    return new CacheRevision(
        revisionId,
        newCommit,
        collectRename(oldTree, newTree),
        fileChange,
        branches
    );
  }

  @Override
  public void updateRevisions() throws IOException, SVNException {
    while (true) {
      loadRevisions();
      if (!cacheRevisions()) {
        break;
      }
    }
    wrapLockWrite((lockManager) -> {
      lockManager.validateLocks();
      return Boolean.TRUE;
    });
    cacheDb.commit();
  }

  private boolean isTreeEmpty(RevTree tree) throws IOException {
    return new CanonicalTreeParser(GitRepository.emptyBytes, repository.newObjectReader(), tree).eof();
  }

  private void loadRevisionInfo(@NotNull RevCommit commit) throws IOException, SVNException {
    final RevWalk revWalk = new RevWalk(repository.newObjectReader());
    final CacheRevision cacheRevision = LayoutHelper.loadCacheRevision(revWalk.getObjectReader(), commit);
    final int revisionId = cacheRevision.getRevisionId();
    final Map<String, VcsCopyFrom> copyFroms = new HashMap<>();
    for (Map.Entry<String, String> entry : cacheRevision.getRenames().entrySet()) {
      copyFroms.put(entry.getKey(), new VcsCopyFrom(revisionId - 1, entry.getValue()));
    }
    final RevCommit oldCommit = revisions.isEmpty() ? null : revisions.get(revisions.size() - 1).getGitNewCommit();
    final RevCommit svnCommit = cacheRevision.getGitCommitId() != null ? revWalk.parseCommit(cacheRevision.getGitCommitId()) : null;
    for (Map.Entry<String, CacheChange> entry : cacheRevision.getFileChange().entrySet()) {
      lastUpdates.compute(entry.getKey(), (key, list) -> {
        final IntList result = list == null ? new IntList() : list;
        result.add(revisionId);
        if (entry.getValue().getNewFile() == null) {
          result.add(MARK_NO_FILE);
        }
        return result;
      });
    }
    final GitRevision revision = new GitRevision(this, commit.getId(), cacheRevision.getRevisionId(), copyFroms, oldCommit, svnCommit, commit.getCommitTime());
    if (cacheRevision.getRevisionId() > 0) {
      if (revisionByDate.isEmpty() || revisionByDate.lastKey() <= revision.getDate()) {
        revisionByDate.put(revision.getDate(), revision);
      }
    }
    if (svnCommit != null) {
      revisionByHash.put(svnCommit.getId(), revision);
    }
    revisions.add(revision);
  }

  @NotNull
  private Map<String, String> collectRename(@NotNull GitFile oldTree, @NotNull GitFile newTree) throws IOException {
    if (!renameDetection) {
      return Collections.emptyMap();
    }
    final GitObject<ObjectId> oldTreeId = oldTree.getObjectId();
    final GitObject<ObjectId> newTreeId = newTree.getObjectId();
    if (oldTreeId == null || newTreeId == null || !Objects.equals(oldTreeId.getRepo(), newTreeId.getRepo())) {
      return Collections.emptyMap();
    }
    final TreeWalk tw = new TreeWalk(repository);
    tw.setRecursive(true);
    tw.addTree(oldTree.getObjectId().getObject());
    tw.addTree(newTree.getObjectId().getObject());

    final RenameDetector rd = new RenameDetector(repository);
    rd.addAll(DiffEntry.scan(tw));

    final Map<String, String> result = new HashMap<>();
    for (DiffEntry diff : rd.compute(tw.getObjectReader(), null)) {
      if (diff.getScore() >= rd.getRenameScore()) {
        result.put(StringHelper.normalize(diff.getNewPath()), StringHelper.normalize(diff.getOldPath()));
      }
    }
    return result;
  }

  @NotNull
  public GitProperty[] collectProperties(@NotNull GitTreeEntry treeEntry, @NotNull VcsSupplier<Iterable<GitTreeEntry>> entryProvider) throws IOException, SVNException {
    if (treeEntry.getFileMode().getObjectType() == Constants.OBJ_BLOB)
      return GitProperty.emptyArray;

    GitProperty[] props = directoryPropertyCache.get(treeEntry.getObjectId().getObject());
    if (props == null) {
      final List<GitProperty> propList = new ArrayList<>();
      for (GitTreeEntry entry : entryProvider.get()) {
        final GitProperty[] parseProps = parseGitProperty(entry.getFileName(), entry.getObjectId());
        if (parseProps.length > 0) {
          propList.addAll(Arrays.asList(parseProps));
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

  @NotNull
  public GitFilter getFilter(@NotNull FileMode fileMode, @NotNull GitProperty[] props) throws IOException, SVNException {
    if (fileMode.getObjectType() != Constants.OBJ_BLOB) {
      return gitFilters.get(GitFilterRaw.NAME);
    }
    if (fileMode == FileMode.SYMLINK) {
      return gitFilters.get(GitFilterLink.NAME);
    }
    for (int i = props.length - 1; i >= 0; --i) {
      final String filterName = props[i].getFilterName();
      if (filterName != null) {
        final GitFilter filter = gitFilters.get(filterName);
        if (filter == null) {
          throw new InvalidStreamException("Unknown filter requested: " + filterName);
        }
        return filter;
      }
    }
    return gitFilters.get(GitFilterRaw.NAME);
  }

  @NotNull
  private GitProperty[] parseGitProperty(@NotNull String fileName, @NotNull GitObject<ObjectId> objectId) throws IOException, SVNException {
    final GitPropertyFactory factory = PropertyMapping.getFactory(fileName);
    if (factory == null)
      return GitProperty.emptyArray;

    return cachedParseGitProperty(objectId, factory);
  }

  @NotNull
  private GitProperty[] cachedParseGitProperty(GitObject<ObjectId> objectId, GitPropertyFactory factory) throws IOException, SVNException {
    GitProperty[] property = filePropertyCache.get(objectId.getObject());
    if (property == null) {
      property = factory.create(loadContent(objectId));
      if (property.length == 0) {
        property = GitProperty.emptyArray;
      }
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
  public VcsRevision getRevisionByDate(long dateTime) throws IOException {
    lock.readLock().lock();
    try {
      final Map.Entry<Long, GitRevision> entry = revisionByDate.floorEntry(dateTime);
      if (entry != null) {
        return entry.getValue();
      }
      return revisions.get(0);
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

  public boolean isObjectBinary(@Nullable GitFilter filter, @Nullable GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    if (objectId == null || filter == null) return false;
    final String key = filter.getName() + " " + objectId.getObject().name();
    Boolean result = binaryCache.get(key);
    if (result == null) {
      try (InputStream stream = filter.inputStream(objectId)) {
        result = SVNFileUtil.detectMimeType(stream) != null;
      }
      binaryCache.putIfAbsent(key, result);
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

  @NotNull
  public GitRevision getRevision(@NotNull ObjectId revisionId) throws SVNException {
    lock.readLock().lock();
    try {
      final GitRevision revision = revisionByHash.get(revisionId);
      if (revision == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revisionId.name()));
      }
      return revision;
    } finally {
      lock.readLock().unlock();
    }
  }

  @NotNull
  @Override
  public VcsWriter createWriter() throws SVNException, IOException {
    return new GitWriter(this, pushMode, pushLock, gitBranch);
  }

  @Override
  public int getLastChange(@NotNull String nodePath, int beforeRevision) {
    if (nodePath.isEmpty()) return beforeRevision;
    final IntList revs = this.lastUpdates.get(nodePath);
    if (revs != null) {
      int prev = 0;
      for (int i = revs.size() - 1; i >= 0; --i) {
        final int rev = revs.get(i);
        if ((rev >= 0) && (rev <= beforeRevision)) {
          if (prev == MARK_NO_FILE) {
            return MARK_NO_FILE;
          }
          return rev;
        }
        prev = rev;
      }
    }
    return MARK_NO_FILE;
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

  @NotNull
  @Override
  public <T> T wrapLockRead(@NotNull LockWorker<T, LockManagerRead> work) throws SVNException, IOException {
    return lockManagerFactory.wrapLockRead(this, work);
  }

  @NotNull
  @Override
  public <T> T wrapLockWrite(@NotNull LockWorker<T, LockManagerWrite> work) throws SVNException, IOException {
    return lockManagerFactory.wrapLockWrite(this, work);
  }

  private static class ComputeBranchName implements BiFunction<RevCommit, CacheInfo, CacheInfo> {
    @NotNull
    private final String svnBranch;

    public ComputeBranchName(@NotNull String svnBranch) {
      this.svnBranch = svnBranch;
    }

    @NotNull
    @Override
    public CacheInfo apply(@NotNull RevCommit revCommit, @NotNull CacheInfo old) {
      if (old.svnBranch == null || LayoutHelper.compareBranches(old.svnBranch, svnBranch) > 0) {
        old.svnBranch = svnBranch;
      }
      return old;
    }
  }

}
