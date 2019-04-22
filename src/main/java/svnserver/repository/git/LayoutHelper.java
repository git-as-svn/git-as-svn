/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.repository.git.layout.RefMappingDirect;
import svnserver.repository.git.layout.RefMappingGroup;
import svnserver.repository.git.layout.RefMappingPrefix;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Helper for creating svn layout in git repository.
 *
 * @author a.navrotskiy
 */
public final class LayoutHelper {
  @NotNull
  private static final RefMappingGroup layoutMapping = new RefMappingGroup(
      new RefMappingDirect(Constants.R_HEADS + Constants.MASTER, "trunk/"),
      new RefMappingPrefix(Constants.R_HEADS, "branches/"),
      new RefMappingPrefix(Constants.R_TAGS, "tags/")
  );
  @NotNull
  private static final String OLD_CACHE_REF = "refs/git-as-svn/v0";
  @NotNull
  private static final String PREFIX_REF = "refs/git-as-svn/v1/";
  @NotNull
  private static final String ENTRY_COMMIT_REF = "commit.ref";
  @NotNull
  private static final String ENTRY_ROOT = "svn";
  @NotNull
  private static final String ENTRY_UUID = "uuid";
  @NotNull
  private static final String PREFIX_ANONIMOUS = "unnamed/";

  @NotNull
  public static Ref initRepository(@NotNull Repository repository, String branch) throws IOException {
    Ref ref = repository.exactRef(PREFIX_REF + branch);
    if (ref == null) {
      Ref old = repository.exactRef(OLD_CACHE_REF);
      if (old != null) {
        final RefUpdate refUpdate = repository.updateRef(PREFIX_REF + branch);
        refUpdate.setNewObjectId(old.getObjectId());
        refUpdate.update();
      }
    }
    if (ref == null) {
      final ObjectId revision = createFirstRevision(repository);
      final RefUpdate refUpdate = repository.updateRef(PREFIX_REF + branch);
      refUpdate.setNewObjectId(revision);
      refUpdate.update();
      ref = repository.exactRef(PREFIX_REF + branch);
      if (ref == null) {
        throw new IOException("Can't initialize repository.");
      }
    }
    Ref old = repository.exactRef(OLD_CACHE_REF);
    if (old != null) {
      final RefUpdate refUpdate = repository.updateRef(OLD_CACHE_REF);
      refUpdate.setForceUpdate(true);
      refUpdate.delete();
    }
    return ref;
  }

  /**
   * Get active branches with commits from repository.
   *
   * @param repository Repository.
   * @return Branches with commits.
   * @throws IOException
   */
  public static Map<String, RevCommit> getBranches(@NotNull Repository repository) throws IOException {
    final RevWalk revWalk = new RevWalk(repository);
    final Map<String, RevCommit> result = new TreeMap<>();
    for (Ref ref : repository.getRefDatabase().getRefs()) {
      try {
        final String svnPath = layoutMapping.gitToSvn(ref.getName());
        if (svnPath != null) {
          final RevCommit revCommit = unwrapCommit(revWalk, ref.getObjectId());
          if (revCommit != null) {
            result.put(svnPath, revCommit);
          }
        }
      } catch (MissingObjectException ignored) {
      }
    }
    return result;
  }

  public static ObjectId createCacheCommit(@NotNull ObjectInserter inserter, @NotNull ObjectId parent, @NotNull RevCommit commit, int revisionId, @NotNull Map<String, ObjectId> revBranches) throws IOException {
    final TreeFormatter treeBuilder = new TreeFormatter();
    treeBuilder.append(ENTRY_COMMIT_REF, commit);
    treeBuilder.append("svn", FileMode.TREE, createSvnLayoutTree(inserter, revBranches));

    new ObjectChecker().checkTree(treeBuilder.toByteArray());
    final ObjectId rootTree = inserter.insert(treeBuilder);

    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(commit.getAuthorIdent());
    commitBuilder.setCommitter(commit.getCommitterIdent());
    commitBuilder.setMessage("#" + revisionId + ": " + commit.getFullMessage());
    commitBuilder.addParentId(parent);
    // Add reference to original commit as parent for prevent commit removing by `git gc` (see #118).
    commitBuilder.addParentId(commit);
    commitBuilder.setTreeId(rootTree);
    return inserter.insert(commitBuilder);
  }

  @Nullable
  public static RevCommit loadOriginalCommit(@NotNull ObjectReader reader, @Nullable ObjectId cacheCommit) throws IOException {
    final RevWalk revWalk = new RevWalk(reader);
    if (cacheCommit != null) {
      final RevCommit revCommit = revWalk.parseCommit(cacheCommit);
      revWalk.parseTree(revCommit.getTree());

      final CanonicalTreeParser treeParser = new CanonicalTreeParser(GitRepository.emptyBytes, reader, revCommit.getTree());
      while (!treeParser.eof()) {
        if (treeParser.getEntryPathString().equals(ENTRY_COMMIT_REF)) {
          return revWalk.parseCommit(treeParser.getEntryObjectId());
        }
        treeParser.next();
      }
    }
    return null;
  }

  /**
   * Unwrap commit from reference.
   *
   * @param revWalk  Git parser.
   * @param objectId Reference object.
   * @return Wrapped commit or null (ex: tag on tree).
   * @throws IOException .
   */
  @Nullable
  private static RevCommit unwrapCommit(@NotNull RevWalk revWalk, @NotNull ObjectId objectId) throws IOException {
    RevObject revObject = revWalk.parseAny(objectId);
    while (true) {
      if (revObject instanceof RevCommit) {
        return (RevCommit) revObject;
      }
      if (revObject instanceof RevTag) {
        revObject = ((RevTag) revObject).getObject();
        continue;
      }
      return null;
    }
  }

  /**
   * Get new revisions list.
   *
   * @param repository    Repository.
   * @param loaded        Already loaded commits.
   * @param targetCommits Target commits.
   * @return Return new commits ordered by creation time. Parent revision always are before child.
   */
  public static List<RevCommit> getNewRevisions(@NotNull Repository repository, @NotNull Set<? extends ObjectId> loaded, @NotNull Collection<? extends ObjectId> targetCommits) throws IOException {
    final Map<RevCommit, RevisionNode> revisionChilds = new HashMap<>();
    final Deque<RevCommit> revisionFirst = new ArrayDeque<>();
    final Deque<RevCommit> revisionQueue = new ArrayDeque<>();
    final RevWalk revWalk = new RevWalk(repository);
    for (ObjectId target : targetCommits) {
      if (!loaded.contains(target)) {
        final RevCommit revCommit = revWalk.parseCommit(target);
        revisionQueue.add(revCommit);
        revisionChilds.put(revCommit, new RevisionNode());
      }
    }
    while (!revisionQueue.isEmpty()) {
      final RevCommit commit = revWalk.parseCommit(revisionQueue.remove());
      if (commit == null || loaded.contains(commit.getId())) {
        revisionFirst.add(commit);
        continue;
      }
      if (commit.getParentCount() > 0) {
        final RevisionNode commitNode = revisionChilds.get(commit);
        for (RevCommit parent : commit.getParents()) {
          commitNode.parents.add(parent);
          revisionChilds.computeIfAbsent(parent, (id) -> {
            revisionQueue.add(parent);
            return new RevisionNode();
          }).childs.add(commit);
        }
      } else {
        revisionFirst.add(commit);
      }
    }
    final List<RevCommit> result = new ArrayList<>(revisionChilds.size());
    while (!revisionChilds.isEmpty()) {
      RevCommit firstCommit = null;
      RevisionNode firstNode = null;
      final Iterator<RevCommit> iterator = revisionFirst.iterator();
      while (iterator.hasNext()) {
        final RevCommit iterCommit = iterator.next();
        final RevisionNode iterNode = revisionChilds.get(iterCommit);
        if (iterNode == null) {
          iterator.remove();
          continue;
        }
        if (!iterNode.parents.isEmpty()) {
          iterator.remove();
        } else if (firstCommit == null || firstCommit.getCommitTime() > iterCommit.getCommitTime()) {
          firstNode = iterNode;
          firstCommit = iterCommit;
        }
      }
      if (firstNode == null || firstCommit == null) {
        throw new IllegalStateException();
      }
      revisionChilds.remove(firstCommit);
      result.add(firstCommit);
      for (RevCommit childId : firstNode.childs) {
        final RevisionNode childNode = revisionChilds.get(childId);
        if (childNode != null) {
          childNode.parents.remove(firstCommit);
          if (childNode.parents.isEmpty()) {
            revisionFirst.add(childId);
          }
        }
      }
    }
    return result;
  }

  public static int compareBranches(@NotNull String branch1, @NotNull String branch2) {
    final int p1 = layoutMapping.getPriority(branch1);
    final int p2 = layoutMapping.getPriority(branch2);
    if (p1 != p2) {
      return p1 - p2;
    }
    return branch1.compareTo(branch2);
  }

  public static String getAnonimousBranch(RevCommit commit) {
    return PREFIX_ANONIMOUS + commit.getId().abbreviate(6).name() + '/';
  }

  @NotNull
  public static String loadRepositoryId(@NotNull ObjectReader objectReader, ObjectId commit) throws IOException {
    RevWalk revWalk = new RevWalk(objectReader);
    TreeWalk treeWalk = TreeWalk.forPath(objectReader, ENTRY_UUID, revWalk.parseCommit(commit).getTree());
    if (treeWalk != null) {
      return GitRepository.loadContent(objectReader, treeWalk.getObjectId(0));
    }
    throw new FileNotFoundException(ENTRY_UUID);
  }

  private static class RevisionNode {
    @NotNull
    private final Set<RevCommit> childs = new HashSet<>();
    @NotNull
    private final Set<RevCommit> parents = new HashSet<>();
  }

  @NotNull
  private static ObjectId createFirstRevision(@NotNull Repository repository) throws IOException {
    // Generate UUID.
    final ObjectInserter inserter = repository.newObjectInserter();
    ObjectId uuidId = inserter.insert(Constants.OBJ_BLOB, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    // Create svn empty tree.
    final ObjectId treeId = inserter.insert(new TreeFormatter());
    // Create commit tree.
    final TreeFormatter rootBuilder = new TreeFormatter();
    rootBuilder.append(ENTRY_ROOT, FileMode.TREE, treeId);
    rootBuilder.append(ENTRY_UUID, FileMode.REGULAR_FILE, uuidId);
    new ObjectChecker().checkTree(rootBuilder.toByteArray());
    final ObjectId rootId = inserter.insert(rootBuilder);
    // Create first commit with message.
    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(new PersonIdent("", "", 0, 0));
    commitBuilder.setCommitter(new PersonIdent("", "", 0, 0));
    commitBuilder.setMessage("#0: Initial revision");
    commitBuilder.setTreeId(rootId);
    final ObjectId commitId = inserter.insert(commitBuilder);
    inserter.flush();
    return commitId;
  }

  @Nullable
  private static ObjectId createSvnLayoutTree(@NotNull ObjectInserter inserter, @NotNull Map<String, ObjectId> revBranches) throws IOException {
    final Deque<TreeFormatter> stack = new ArrayDeque<>();
    stack.add(new TreeFormatter());
    String dir = "";
    final ObjectChecker checker = new ObjectChecker();
    for (Map.Entry<String, ObjectId> entry : new TreeMap<>(revBranches).entrySet()) {
      final String path = entry.getKey();
      // Save already added nodes.
      while (!path.startsWith(dir)) {
        final int index = dir.lastIndexOf('/', dir.length() - 2) + 1;
        final TreeFormatter tree = stack.pop();
        checker.checkTree(tree.toByteArray());
        stack.element().append(dir.substring(index, dir.length() - 1), FileMode.TREE, inserter.insert(tree));
        dir = dir.substring(0, index);
      }
      // Go deeper.
      for (int index = path.indexOf('/', dir.length()) + 1; index < path.length(); index = path.indexOf('/', index) + 1) {
        dir = path.substring(0, index);
        stack.push(new TreeFormatter());
      }
      // Add commit to tree.
      {
        final int index = path.lastIndexOf('/', path.length() - 2) + 1;
        stack.element().append(path.substring(index, path.length() - 1), FileMode.GITLINK, entry.getValue());
      }
    }
    // Save already added nodes.
    while (!dir.isEmpty()) {
      int index = dir.lastIndexOf('/', dir.length() - 2) + 1;
      final TreeFormatter tree = stack.pop();
      checker.checkTree(tree.toByteArray());
      stack.element().append(dir.substring(index, dir.length() - 1), FileMode.TREE, inserter.insert(tree));
      dir = dir.substring(0, index);
    }
    // Save root tree to disk.
    final TreeFormatter rootTree = stack.pop();
    checker.checkTree(rootTree.toByteArray());
    if (!stack.isEmpty()) {
      throw new IllegalStateException();
    }
    return inserter.insert(rootTree);
  }
}
