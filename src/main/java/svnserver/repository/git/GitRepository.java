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
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import svnserver.StringHelper;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
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
import svnserver.repository.git.push.GitPusher;
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
  static final byte[] emptyBytes = new byte[0];
  @NotNull
  private final Repository repository;
  @NotNull
  private final GitPusher pusher;
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
  private final LocalContext context;
  @NotNull
  private final HTreeMap<String, Boolean> binaryCache;
  @NotNull
  private final HTreeMap<String, byte[]> revisionCache;
  @NotNull
  private final Map<String, GitFilter> gitFilters;
  @NotNull
  private final Map<ObjectId, GitProperty[]> directoryPropertyCache = new ConcurrentHashMap<>();
  @NotNull
  private final Map<ObjectId, GitProperty[]> filePropertyCache = new ConcurrentHashMap<>();
  private final boolean renameDetection;

  public GitRepository(@NotNull LocalContext context,
                       @NotNull Repository repository,
                       @NotNull GitPusher pusher,
                       @NotNull String branch,
                       boolean renameDetection,
                       @NotNull LockManagerFactory lockManagerFactory) throws IOException {
    this.context = context;
    final SharedContext shared = context.getShared();
    shared.getOrCreate(GitSubmodules.class, GitSubmodules::new).register(repository);
    this.repository = repository;
    this.binaryCache = shared.getCacheDB().hashMap("cache.binary", Serializer.STRING, Serializer.BOOLEAN).createOrOpen();
    this.revisionCache = context.getShared().getCacheDB().hashMap(String.format("cache-revision.%s.%s", context.getName(), renameDetection ? "1" : "0"), Serializer.STRING, Serializer.BYTE_ARRAY).createOrOpen();
    this.pusher = pusher;
    this.renameDetection = renameDetection;
    this.lockManagerFactory = lockManagerFactory;
    this.gitFilters = GitFilterHelper.createFilters(context);

    final Ref svnBranchRef = LayoutHelper.initRepository(repository, branch);
    this.svnBranch = svnBranchRef.getName();
    this.gitBranch = Constants.R_HEADS + branch;
    final String repositoryId = loadRepositoryId(repository, svnBranchRef);
    this.uuid = UUID.nameUUIDFromBytes((repositoryId + "\0" + gitBranch).getBytes(StandardCharsets.UTF_8)).toString();

    log.info("[{}]: registered branch: {}", context.getName(), gitBranch);
  }

  @NotNull
  @Override
  public LocalContext getContext() {
    return context;
  }

  @Override
  public void close() {
    context.getShared().sure(GitSubmodules.class).unregister(repository);
  }

  @NotNull
  private static String loadRepositoryId(@NotNull Repository repository, @NotNull Ref ref) throws IOException {
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
  private void loadRevisions() throws IOException, SVNException {
    // Fast check.
    lock.readLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      final ObjectId lastCommitId;
      if (lastRevision >= 0) {
        lastCommitId = revisions.get(lastRevision).getCacheCommit();
        final Ref head = repository.exactRef(svnBranch);
        if (head.getObjectId().equals(lastCommitId)) {
          return;
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
      final Ref head = repository.exactRef(svnBranch);
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
        return;
      }
      final long beginTime = System.currentTimeMillis();
      int processed = 0;
      long reportTime = beginTime;
      log.info("[{}]: loading cached revision changes: {} revisions", context.getName(), newRevs.size());
      for (int i = newRevs.size() - 1; i >= 0; i--) {
        loadRevisionInfo(newRevs.get(i));
        processed++;
        long currentTime = System.currentTimeMillis();
        if (currentTime - reportTime > REPORT_DELAY) {
          log.info("[{}]: processed cached revision: {}/{} ({} rev/sec)", context.getName(), newRevs.size() - i, newRevs.size(), 1000.0f * processed / (currentTime - reportTime));
          reportTime = currentTime;
          processed = 0;
        }
      }
      final long endTime = System.currentTimeMillis();
      log.info("[{}]: {} cached revision loaded: {} ms", context.getName(), newRevs.size(), endTime - beginTime);
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
  public boolean cacheRevisions() throws IOException {
    // Fast check.
    lock.readLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      if (lastRevision >= 0) {
        final ObjectId lastCommitId = revisions.get(lastRevision).getGitNewCommit();
        final Ref master = repository.exactRef(gitBranch);
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
      final Ref master = repository.exactRef(gitBranch);
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
        log.info("[{}]: Loading revision changes: {} revision", context.getName(), newRevs.size());
        int revisionId = revisions.size();
        ObjectId cacheId = revisions.get(revisions.size() - 1).getCacheCommit();
        for (int i = newRevs.size() - 1; i >= 0; i--) {
          final RevCommit revCommit = newRevs.get(i);
          cacheId = LayoutHelper.createCacheCommit(inserter, cacheId, revCommit, revisionId, Collections.emptyMap());
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

  @NotNull
  private CacheRevision loadCacheRevision(@NotNull ObjectReader reader, @NotNull RevCommit newCommit, int revisionId) throws IOException, SVNException {
    final String cacheKey = newCommit.name();

    CacheRevision result = CacheRevision.deserialize(revisionCache.get(cacheKey));
    if (result == null) {
      final RevCommit baseCommit = LayoutHelper.loadOriginalCommit(reader, newCommit);
      final GitFile oldTree = getSubversionTree(reader, newCommit.getParentCount() > 0 ? newCommit.getParent(0) : null, revisionId - 1);
      final GitFile newTree = getSubversionTree(reader, newCommit, revisionId);
      final Map<String, CacheChange> fileChange = new TreeMap<>();
      for (Map.Entry<String, GitLogPair> entry : ChangeHelper.collectChanges(oldTree, newTree, true).entrySet()) {
        fileChange.put(entry.getKey(), new CacheChange(entry.getValue()));
      }
      result = new CacheRevision(
          baseCommit,
          collectRename(oldTree, newTree),
          fileChange
      );
      revisionCache.put(cacheKey, CacheRevision.serialize(result));
    }
    return result;
  }

  @NotNull
  private GitFile getSubversionTree(@NotNull ObjectReader reader, @Nullable RevCommit commit, int revisionId) throws IOException, SVNException {
    final RevCommit revCommit = LayoutHelper.loadOriginalCommit(reader, commit);
    if (revCommit == null) {
      return new GitFileEmptyTree(this, "", revisionId - 1);
    }
    return GitFileTreeEntry.create(this, revCommit.getTree(), revisionId);
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
    context.getShared().getCacheDB().commit();
  }

  private boolean isTreeEmpty(RevTree tree) throws IOException {
    return new CanonicalTreeParser(GitRepository.emptyBytes, repository.newObjectReader(), tree).eof();
  }

  private void loadRevisionInfo(@NotNull RevCommit commit) throws IOException, SVNException {
    final ObjectReader reader = repository.newObjectReader();
    final CacheRevision cacheRevision = loadCacheRevision(reader, commit, revisions.size());
    final int revisionId = revisions.size();
    final Map<String, VcsCopyFrom> copyFroms = new HashMap<>();
    for (Map.Entry<String, String> entry : cacheRevision.getRenames().entrySet()) {
      copyFroms.put(entry.getKey(), new VcsCopyFrom(revisionId - 1, entry.getValue()));
    }
    final RevCommit oldCommit = revisions.isEmpty() ? null : revisions.get(revisions.size() - 1).getGitNewCommit();
    final RevCommit svnCommit = cacheRevision.getGitCommitId() != null ? new RevWalk(reader).parseCommit(cacheRevision.getGitCommitId()) : null;
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
    final GitRevision revision = new GitRevision(this, commit.getId(), revisionId, copyFroms, oldCommit, svnCommit, commit.getCommitTime());
    if (revision.getId() > 0) {
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
      try {
        for (GitTreeEntry entry : entryProvider.get()) {
          final GitProperty[] parseProps = parseGitProperty(entry.getFileName(), entry.getObjectId());
          if (parseProps.length > 0) {
            propList.addAll(Arrays.asList(parseProps));
          }
        }
      } catch (SvnForbiddenException ignored) {
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
  public GitFilter getFilter(@NotNull FileMode fileMode, @NotNull GitProperty[] props) {
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
  private GitProperty[] cachedParseGitProperty(GitObject<ObjectId> objectId, GitPropertyFactory factory) throws IOException {
    GitProperty[] property = filePropertyCache.get(objectId.getObject());
    if (property == null) {
      property = factory.create(loadContent(objectId.getRepo().newObjectReader(), objectId.getObject()));
      if (property.length == 0) {
        property = GitProperty.emptyArray;
      }
      filePropertyCache.put(objectId.getObject(), property);
    }
    return property;
  }

  @NotNull
  @Override
  public GitRevision getLatestRevision() {
    lock.readLock().lock();
    try {
      return revisions.get(revisions.size() - 1);
    } finally {
      lock.readLock().unlock();
    }
  }

  @NotNull
  @Override
  public VcsRevision getRevisionByDate(long dateTime) {
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
  public GitRevision getRevisionInfo(int revision) throws SVNException {
    final GitRevision revisionInfo = getRevisionInfoUnsafe(revision);
    if (revisionInfo == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revision));
    }
    return revisionInfo;
  }

  @NotNull
  public GitRevision sureRevisionInfo(int revision) {
    final GitRevision revisionInfo = getRevisionInfoUnsafe(revision);
    if (revisionInfo == null) {
      throw new IllegalStateException("No such revision " + revision);
    }
    return revisionInfo;
  }

  @Nullable
  private GitRevision getRevisionInfoUnsafe(int revision) {
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
  public VcsWriter createWriter(@NotNull User user) throws SVNException {
    if (user.getEmail() == null || user.getEmail().isEmpty()) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Users with undefined email can't create commits"));
    }
    return new GitWriter(this, pusher, pushLock, gitBranch, user);
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
  public static String loadContent(@NotNull ObjectReader reader, @NotNull ObjectId objectId) throws IOException {
    final byte[] bytes = reader.open(objectId).getCachedBytes();
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
        throw new SvnForbiddenException();
      }
      return new GitObject<>(linkedCommit.getRepo(), linkedCommit.getObject().getTree());
    } else {
      return null;
    }
  }

  @Nullable
  public GitObject<RevCommit> loadLinkedCommit(@NotNull ObjectId objectId) throws IOException {
    return context.getShared().sure(GitSubmodules.class).findCommit(objectId);
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
