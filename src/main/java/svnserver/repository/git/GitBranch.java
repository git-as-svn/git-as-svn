/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.auth.User;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.git.cache.CacheChange;
import svnserver.repository.git.cache.CacheRevision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class GitBranch {
  private static final int revisionCacheVersion = 2;
  private static final int REPORT_DELAY = 2500;
  private static final int MARK_NO_FILE = -1;
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitBranch.class);
  @NotNull
  private final String uuid;
  @NotNull
  private final String gitBranch;
  @NotNull
  private final String svnBranch;
  @NotNull
  private final GitRepository repository;
  private final String shortBranchName;
  /**
   * Lock for prevent concurrent pushes.
   */
  @NotNull
  private final Object pushLock = new Object();
  @NotNull
  private final List<GitRevision> revisions = new ArrayList<>();
  @NotNull
  private final TreeMap<Long, GitRevision> revisionByDate = new TreeMap<>();
  @NotNull
  private final Map<ObjectId, GitRevision> revisionByHash = new HashMap<>();
  @NotNull
  private final HTreeMap<ObjectId, CacheRevision> revisionCache;
  @NotNull
  private final ReadWriteLock lastUpdatesLock = new ReentrantReadWriteLock();
  @NotNull
  private final Map<String, int[]> lastUpdates = new HashMap<>();
  @NotNull
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public GitBranch(@NotNull GitRepository repository, @NotNull String branch) throws IOException {
    this.repository = repository;
    shortBranchName = branch;

    final Ref svnBranchRef = LayoutHelper.initRepository(repository.getGit(), branch);
    this.svnBranch = svnBranchRef.getName();
    this.gitBranch = Constants.R_HEADS + branch;
    final String repositoryId = loadRepositoryId(repository.getGit(), svnBranchRef);
    this.uuid = UUID.nameUUIDFromBytes((repositoryId + "\0" + gitBranch).getBytes(StandardCharsets.UTF_8)).toString();

    final String revisionCacheName = String.format(
        "cache-revision.%s.%s.%s.v%s", repository.getContext().getName(), gitBranch, repository.hasRenameDetection() ? 1 : 0, revisionCacheVersion
    );
    this.revisionCache = repository.getContext().getShared().getCacheDB().hashMap(
        revisionCacheName,
        ObjectIdSerializer.instance,
        CacheRevisionSerializer.instance
    ).createOrOpen();
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

  @NotNull
  public GitRepository getRepository() {
    return repository;
  }

  @NotNull
  public GitRevision getRevisionInfo(int revision) throws SVNException {
    final GitRevision revisionInfo = getRevisionInfoUnsafe(revision);
    if (revisionInfo == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revision));
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
  public GitRevision getLatestRevision() {
    lock.readLock().lock();
    try {
      return revisions.get(revisions.size() - 1);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void updateRevisions() throws IOException, SVNException {
    boolean gotNewRevisions = false;

    while (true) {
      loadRevisions();
      if (!cacheRevisions()) {
        break;
      }
      gotNewRevisions = true;
    }

    if (gotNewRevisions) {
      final boolean locksChanged = repository.wrapLockWrite(lockManager -> lockManager.cleanupInvalidLocks(this));
      if (locksChanged)
        repository.getContext().getShared().getCacheDB().commit();
    }
  }

  /**
   * Load all cached revisions.
   */
  private void loadRevisions() throws IOException, SVNException {
    // Fast check.
    lock.readLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      final ObjectId lastCommitId;
      if (lastRevision >= 0) {
        lastCommitId = revisions.get(lastRevision).getCacheCommit();
        final Ref head = repository.getGit().exactRef(svnBranch);
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
      final Ref head = repository.getGit().exactRef(svnBranch);
      final List<RevCommit> newRevs = new ArrayList<>();
      final RevWalk revWalk = new RevWalk(repository.getGit());
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
      log.info("[{}]: loading cached revision changes: {} revisions", this, newRevs.size());
      for (int i = newRevs.size() - 1; i >= 0; i--) {
        loadRevisionInfo(newRevs.get(i));
        processed++;
        long currentTime = System.currentTimeMillis();
        if (currentTime - reportTime > REPORT_DELAY) {
          log.info("[{}]: processed cached revision: {}/{} ({} rev/sec)", this, newRevs.size() - i, newRevs.size(), 1000.0f * processed / (currentTime - reportTime));
          reportTime = currentTime;
          processed = 0;
        }
      }
      final long endTime = System.currentTimeMillis();
      log.info("[{}]: {} cached revision loaded: {} ms", this, newRevs.size(), endTime - beginTime);
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Create cache for new revisions.
   */
  private boolean cacheRevisions() throws IOException {
    // Fast check.
    lock.readLock().lock();
    try {
      final int lastRevision = revisions.size() - 1;
      if (lastRevision >= 0) {
        final ObjectId lastCommitId = revisions.get(lastRevision).getGitNewCommit();
        final Ref master = repository.getGit().exactRef(gitBranch);
        if ((master == null) || (master.getObjectId().equals(lastCommitId))) {
          return false;
        }
      }
    } finally {
      lock.readLock().unlock();
    }
    // Real update.
    final ObjectInserter inserter = repository.getGit().newObjectInserter();
    lock.writeLock().lock();
    try {
      final Ref master = repository.getGit().exactRef(gitBranch);
      final List<RevCommit> newRevs = new ArrayList<>();
      final RevWalk revWalk = new RevWalk(repository.getGit());
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
        log.info("[{}]: Loading revision changes: {} revision", this, newRevs.size());
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

            final RefUpdate refUpdate = repository.getGit().updateRef(svnBranch);
            refUpdate.setNewObjectId(cacheId);
            refUpdate.update();
          }
          revisionId++;
        }
        final long endTime = System.currentTimeMillis();
        log.info("Revision changes loaded: {} ms", endTime - beginTime);

        final RefUpdate refUpdate = repository.getGit().updateRef(svnBranch);
        refUpdate.setNewObjectId(cacheId);
        refUpdate.update();
      }
      return !newRevs.isEmpty();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void loadRevisionInfo(@NotNull RevCommit commit) throws IOException, SVNException {
    final ObjectReader reader = repository.getGit().newObjectReader();
    final CacheRevision cacheRevision = loadCacheRevision(reader, commit, revisions.size());
    final int revisionId = revisions.size();
    final Map<String, VcsCopyFrom> copyFroms = new HashMap<>();
    for (Map.Entry<String, String> entry : cacheRevision.getRenames().entrySet()) {
      copyFroms.put(entry.getKey(), new VcsCopyFrom(revisionId - 1, entry.getValue()));
    }
    final RevCommit oldCommit = revisions.isEmpty() ? null : revisions.get(revisions.size() - 1).getGitNewCommit();
    final RevCommit svnCommit = cacheRevision.getGitCommitId() != null ? new RevWalk(reader).parseCommit(cacheRevision.getGitCommitId()) : null;

    try {
      lastUpdatesLock.writeLock().lock();
      for (Map.Entry<String, CacheChange> entry : cacheRevision.getFileChange().entrySet()) {
        lastUpdates.compute(entry.getKey(), (key, list) -> {
          final boolean markNoFile = entry.getValue().getNewFile() == null;
          final int prevLen = (list == null ? 0 : list.length);
          final int newLen = prevLen + 1 + (markNoFile ? 1 : 0);
          final int[] result = list == null ? new int[newLen] : Arrays.copyOf(list, newLen);

          result[prevLen] = revisionId;
          if (markNoFile) {
            result[prevLen + 1] = MARK_NO_FILE;
          }
          return result;
        });
      }
    } finally {
      lastUpdatesLock.writeLock().unlock();
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
  private CacheRevision loadCacheRevision(@NotNull ObjectReader reader, @NotNull RevCommit newCommit, int revisionId) throws IOException, SVNException {
    final ObjectId cacheKey = newCommit.copy();

    CacheRevision result = revisionCache.get(cacheKey);
    if (result == null) {
      final RevCommit baseCommit = LayoutHelper.loadOriginalCommit(reader, newCommit);
      final GitFile oldTree = getSubversionTree(reader, newCommit.getParentCount() > 0 ? newCommit.getParent(0) : null, revisionId - 1);
      final GitFile newTree = getSubversionTree(reader, newCommit, revisionId);
      final Map<String, CacheChange> fileChange = new TreeMap<>();
      for (Map.Entry<String, GitLogEntry> entry : ChangeHelper.collectChanges(oldTree, newTree, true).entrySet()) {
        fileChange.put(entry.getKey(), new CacheChange(entry.getValue()));
      }
      result = new CacheRevision(
          baseCommit,
          collectRename(oldTree, newTree),
          fileChange
      );
      revisionCache.put(cacheKey, result);
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

  @NotNull
  private Map<String, String> collectRename(@NotNull GitFile oldTree, @NotNull GitFile newTree) throws IOException {
    if (!repository.hasRenameDetection()) {
      return Collections.emptyMap();
    }
    final GitObject<ObjectId> oldTreeId = oldTree.getObjectId();
    final GitObject<ObjectId> newTreeId = newTree.getObjectId();
    if (oldTreeId == null || newTreeId == null || !Objects.equals(oldTreeId.getRepo(), newTreeId.getRepo())) {
      return Collections.emptyMap();
    }
    final TreeWalk tw = new TreeWalk(repository.getGit());
    tw.setRecursive(true);
    tw.addTree(oldTree.getObjectId().getObject());
    tw.addTree(newTree.getObjectId().getObject());

    final RenameDetector rd = new RenameDetector(repository.getGit());
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
  public GitRevision getRevisionByDate(long dateTime) {
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
  GitRevision sureRevisionInfo(int revision) {
    final GitRevision revisionInfo = getRevisionInfoUnsafe(revision);
    if (revisionInfo == null) {
      throw new IllegalStateException("No such revision " + revision);
    }
    return revisionInfo;
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

  public int getLastChange(@NotNull String nodePath, int beforeRevision) {
    if (nodePath.isEmpty()) return beforeRevision;

    try {
      lastUpdatesLock.readLock().lock();

      final int[] revs = this.lastUpdates.get(nodePath);
      if (revs != null) {
        int prev = 0;
        for (int i = revs.length - 1; i >= 0; --i) {
          final int rev = revs[i];
          if ((rev >= 0) && (rev <= beforeRevision)) {
            if (prev == MARK_NO_FILE) {
              return MARK_NO_FILE;
            }
            return rev;
          }
          prev = rev;
        }
      }
    } finally {
      lastUpdatesLock.readLock().unlock();
    }

    return MARK_NO_FILE;
  }

  @NotNull
  public String getUuid() {
    return uuid;
  }

  @NotNull
  public GitWriter createWriter(@NotNull User user) throws SVNException {
    if (user.getEmail() == null || user.getEmail().isEmpty()) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Users with undefined email can't create commits"));
    }
    return new GitWriter(this, repository.getPusher(), pushLock, gitBranch, user);
  }

  @NotNull
  public String getShortBranchName() {
    return shortBranchName;
  }

  @Override
  public String toString() {
    return repository.getContext().getName() + "@" + shortBranchName;
  }
}
