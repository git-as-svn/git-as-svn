package svnserver.repository.git;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Helper for creating svn layout in git repository.
 *
 * @author a.navrotskiy
 */
public class LayoutHelper {
  @NotNull
  private static final String REF = "refs/git-as-svn/v1";
  @NotNull
  private static final String SVN_ROOT = "svn";
  @NotNull
  private static final String MASTER = Constants.MASTER;
  @NotNull
  private static final String TRUNK = "trunk/";
  @NotNull
  private static final String PREFIX_BRANCH = "branchs/";
  @NotNull
  private static final String PREFIX_ANONIMOUS = "unnamed/";
  @NotNull
  private static final String PREFIX_TAG = "tags/";

  @NotNull
  public static Ref initRepository(@NotNull Repository repository) throws IOException {
    Ref ref = repository.getRef(REF);
    if (ref != null) {
      // todo: Remove after layout cache loading support
      final RefUpdate refUpdate = repository.updateRef(REF);
      refUpdate.delete();
      ref = null;
    }
    if (ref == null) {
      final ObjectId revision = createFirstRevision(repository);
      final RefUpdate refUpdate = repository.updateRef(REF);
      refUpdate.setNewObjectId(revision);
      refUpdate.update();
      ref = repository.getRef(REF);
      if (ref == null) {
        throw new IOException("Can't initialize repository.");
      }
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
    for (Ref ref : repository.getAllRefs().values()) {
      try {
        if (ref.getName().startsWith(Constants.R_HEADS)) {
          final ObjectId objectId = ref.getObjectId();
          final String shortName = ref.getName().substring(Constants.R_HEADS.length());
          result.put(shortName.equals(MASTER) ? TRUNK : (PREFIX_BRANCH + shortName + '/'), revWalk.parseCommit(objectId));
        }
        if (ref.getName().startsWith(Constants.R_TAGS)) {
          final RevTag revTag = revWalk.parseTag(ref.getObjectId());
          final ObjectId objectId = revTag.getObject();
          RevObject revObject = revWalk.parseAny(objectId);
          if (revObject instanceof RevCommit) {
            result.put(PREFIX_TAG + ref.getName().substring(Constants.R_TAGS.length()) + '/', (RevCommit) revObject);
          }
        }
      } catch (MissingObjectException ignored) {
      }
    }
    return result;
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
    final int p1 = getBranchPriority(branch1);
    final int p2 = getBranchPriority(branch2);
    if (p1 != p2) {
      return p1 - p2;
    }
    return branch1.compareTo(branch2);
  }

  public static int getBranchPriority(@NotNull String branchName) {
    if (branchName.equals(TRUNK)) return 0;
    if (branchName.startsWith(PREFIX_BRANCH)) return 1;
    if (branchName.startsWith(PREFIX_TAG)) return 2;
    return 3;
  }

  public static String getAnonimousBranch(RevCommit commit) {
    return PREFIX_ANONIMOUS + commit.getId().abbreviate(6).name() + '/';
  }

  public static Map<String, RevCommit> getRevisionBranches(@NotNull Repository repository, @NotNull GitRevision prev) throws IOException {
    final RevCommit commit = prev.getCommit();
    if (commit == null) {
      return new HashMap<>();
    }
    TreeWalk treeWalk = new TreeWalk(repository);
    treeWalk.addTree(commit.getTree());
    treeWalk.setRecursive(true);
    while (treeWalk.next()) {
      System.out.println("found: " + treeWalk.getPathString());
    }
    return null;
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
    rootBuilder.append(SVN_ROOT, FileMode.TREE, treeId);
    rootBuilder.append("uuid", FileMode.REGULAR_FILE, uuidId);
    new ObjectChecker().checkTree(rootBuilder.toByteArray());
    final ObjectId rootId = inserter.insert(rootBuilder);
    // Create first commit with message.
    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(new PersonIdent("", "", 0, 0));
    commitBuilder.setCommitter(new PersonIdent("", "", 0, 0));
    commitBuilder.setMessage(revisionName(0));
    commitBuilder.setTreeId(rootId);
    final ObjectId commitId = inserter.insert(commitBuilder);
    inserter.flush();
    return commitId;
  }

  @Nullable
  public static ObjectId createSvnLayoutTree(@NotNull ObjectInserter inserter, @NotNull Map<String, RevCommit> revBranches) throws IOException {
    final Deque<TreeFormatter> stack = new ArrayDeque<>();
    stack.add(new TreeFormatter());
    String dir = "";
    final ObjectChecker checker = new ObjectChecker();
    for (Map.Entry<String, RevCommit> entry : new TreeMap<>(revBranches).entrySet()) {
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
        stack.element().append(path.substring(index, path.length() - 1), entry.getValue().getTree());
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

  @NotNull
  private static String revisionName(int rev) {
    return "Revision " + rev;
  }
}
