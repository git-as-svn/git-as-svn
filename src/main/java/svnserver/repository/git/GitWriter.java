/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.WikiConstants;
import svnserver.auth.User;
import svnserver.repository.*;
import svnserver.repository.git.prop.PropertyMapping;
import svnserver.repository.locks.LockDesc;
import svnserver.repository.locks.LockManagerWrite;

import java.io.IOException;
import java.util.*;

/**
 * Git commit writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitWriter implements VcsWriter {
  private static final int MAX_PROPERTY_ERRROS = 50;

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitWriter.class);

  @NotNull
  private final GitRepository repo;
  @NotNull
  private final ObjectInserter inserter;
  @NotNull
  private final GitPushMode pushMode;
  @NotNull
  private final Object pushLock;
  @NotNull
  private final String gitBranch;

  public GitWriter(@NotNull GitRepository repo, @NotNull GitPushMode pushMode, @NotNull Object pushLock, @NotNull String gitBranch) {
    this.repo = repo;
    this.pushMode = pushMode;
    this.pushLock = pushLock;
    this.gitBranch = gitBranch;
    this.inserter = repo.getRepository().newObjectInserter();
  }

  @NotNull
  @Override
  public VcsDeltaConsumer createFile(@NotNull VcsEntry parent, @NotNull String name) throws IOException, SVNException {
    return new GitDeltaConsumer(this, ((GitEntry) parent).createChild(name, false), null);
  }

  @NotNull
  @Override
  public VcsDeltaConsumer modifyFile(@NotNull VcsEntry parent, @NotNull String name, @NotNull VcsFile file) throws IOException, SVNException {
    return new GitDeltaConsumer(this, ((GitEntry) parent).createChild(name, false), (GitFile) file);
  }

  @NotNull
  @Override
  public VcsCommitBuilder createCommitBuilder(@NotNull LockManagerWrite lockManager, @NotNull Map<String, String> locks) throws IOException, SVNException {
    return new GitCommitBuilder(lockManager, locks, gitBranch);
  }

  @NotNull
  public GitRepository getRepository() {
    return repo;
  }

  @NotNull
  public ObjectInserter getInserter() {
    return inserter;
  }

  private class GitCommitBuilder implements VcsCommitBuilder {
    @NotNull
    private final Deque<GitTreeUpdate> treeStack;
    @NotNull
    private final GitRevision revision;
    @NotNull
    private final String branch;
    @NotNull
    private final LockManagerWrite lockManager;
    @NotNull
    private final Map<String, String> locks;
    @NotNull
    private final List<VcsConsumer<CommitAction>> commitActions = new ArrayList<>();

    public GitCommitBuilder(@NotNull LockManagerWrite lockManager, @NotNull Map<String, String> locks, @NotNull String branch) throws IOException, SVNException {
      this.branch = branch;
      this.lockManager = lockManager;
      this.locks = locks;
      this.revision = repo.getLatestRevision();
      this.treeStack = new ArrayDeque<>();
      this.treeStack.push(new GitTreeUpdate("", getOriginalTree()));
    }

    private Iterable<GitTreeEntry> getOriginalTree() throws IOException {
      final RevCommit commit = revision.getGitNewCommit();
      if (commit == null) {
        return Collections.emptyList();
      }
      return repo.loadTree(new GitTreeEntry(repo.getRepository(), FileMode.TREE, commit.getTree(), ""));
    }

    @Override
    public void checkUpToDate(@NotNull String path, int rev, boolean checkLock) throws SVNException, IOException {
      final GitFile file = revision.getFile(path);
      if (file == null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, path));
      } else if (file.getLastChange().getId() > rev) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "Working copy is not up-to-date: " + path));
      }
      if (checkLock) {
        checkLockFile(file);
      }
    }

    private void checkLockFile(@NotNull GitFile file) throws SVNException, IOException {
      final String fullPath = file.getFullPath();
      if (file.isDirectory()) {
        final Iterator<LockDesc> iter = lockManager.getLocks(fullPath, Depth.Infinity);
        while (iter.hasNext()) {
          checkLockDesc(iter.next());
        }
      } else {
        checkLockDesc(lockManager.getLock(fullPath));
      }
    }

    private void checkLockDesc(@Nullable LockDesc lockDesc) throws SVNException {
      if (lockDesc != null) {
        final String token = locks.get(lockDesc.getPath());
        if (token == null || !lockDesc.getToken().equals(token)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_BAD_LOCK_TOKEN, lockDesc.getPath()));
        }
      }
    }

    @Override
    public void addDir(@NotNull String name, @Nullable VcsFile sourceDir) throws SVNException, IOException {
      final GitTreeUpdate current = treeStack.element();
      if (current.getEntries().containsKey(name)) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, getFullPath(name)));
      }
      final GitFile source = (GitFile) sourceDir;
      commitActions.add(action -> action.openDir(name));
      treeStack.push(new GitTreeUpdate(name, repo.loadTree(source == null ? null : source.getTreeEntry())));
    }

    @Override
    public void openDir(@NotNull String name) throws SVNException, IOException {
      final GitTreeUpdate current = treeStack.element();
      final GitTreeEntry originalDir = current.getEntries().remove(name);
      if ((originalDir == null) || (!originalDir.getFileMode().equals(FileMode.TREE))) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)));
      }
      commitActions.add(action -> action.openDir(name));
      treeStack.push(new GitTreeUpdate(name, repo.loadTree(originalDir)));
    }

    @Override
    public void checkDirProperties(@NotNull Map<String, String> props) throws SVNException, IOException {
      commitActions.add(action -> action.checkProperties(null, props, null));
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
      log.debug("Create tree {} for dir: {}", subtreeId.name(), fullPath);
      if (current.getEntries().put(last.getName(), new GitTreeEntry(FileMode.TREE, new GitObject<>(repo.getRepository(), subtreeId), last.getName())) != null) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, fullPath));
      }
      commitActions.add(CommitAction::closeDir);
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
      commitActions.add(action -> action.checkProperties(name, gitDeltaConsumer.getProperties(), gitDeltaConsumer));
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
      final GitTreeUpdate root = treeStack.element();
      ObjectId treeId = root.buildTree(inserter);
      log.debug("Create tree {} for commit.", treeId.name());

      final CommitBuilder commitBuilder = new CommitBuilder();
      final PersonIdent ident = createIdent(userInfo);
      commitBuilder.setAuthor(ident);
      commitBuilder.setCommitter(ident);
      commitBuilder.setMessage(message);
      final RevCommit parentCommit = revision.getGitNewCommit();
      if (parentCommit != null) {
        commitBuilder.setParentId(parentCommit.getId());
      }
      commitBuilder.setTreeId(treeId);
      final ObjectId commitId = inserter.insert(commitBuilder);
      inserter.flush();
      log.info("Create commit {}: {}", commitId.name(), message);

      if (filterMigration(new RevWalk(repo.getRepository()).parseTree(treeId)) != 0) {
        log.info("Need recreate tree after filter migration.");
        return null;
      }

      synchronized (pushLock) {
        log.info("Validate properties");
        validateProperties(new RevWalk(repo.getRepository()).parseTree(treeId));

        log.info("Try to push commit in branch: {}", branch);
        if (!pushMode.push(repo.getRepository(), commitId, branch)) {
          log.info("Non fast forward push rejected");
          return null;
        }
        log.info("Commit is pushed");
        repo.updateRevisions();
        return repo.getRevision(commitId);
      }
    }

    private void validateProperties(@NotNull RevTree tree) throws IOException, SVNException {
      final GitFile root = GitFileTreeEntry.create(repo, tree, 0);
      final GitPropertyValidator validator = new GitPropertyValidator(root);
      for (VcsConsumer<CommitAction> validateAction : commitActions) {
        validateAction.accept(validator);
      }
      validator.done();
    }

    private int filterMigration(@NotNull RevTree tree) throws IOException, SVNException {
      final GitFile root = GitFileTreeEntry.create(repo, tree, 0);
      final GitFilterMigration validator = new GitFilterMigration(root);
      for (VcsConsumer<CommitAction> validateAction : commitActions) {
        validateAction.accept(validator);
      }
      return validator.done();
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

  private abstract class CommitAction {
    @NotNull
    private final Deque<GitFile> treeStack;

    public CommitAction(@NotNull GitFile root) {
      this.treeStack = new ArrayDeque<>();
      this.treeStack.push(root);
    }

    protected GitFile getElement() {
      return treeStack.element();
    }

    public final void openDir(@NotNull String name) throws IOException, SVNException {
      final GitFile file = treeStack.element().getEntry(name);
      if (file == null) {
        throw new IllegalStateException("Invalid state: can't find file " + name + " in created commit.");
      }
      treeStack.push(file);
    }

    public abstract void checkProperties(@Nullable String name, @NotNull Map<String, String> props, @Nullable GitDeltaConsumer deltaConsumer) throws IOException, SVNException;

    public final void closeDir() {
      treeStack.pop();
    }
  }

  private class GitFilterMigration extends CommitAction {
    private int migrateCount = 0;

    public GitFilterMigration(@NotNull GitFile root) {
      super(root);
    }

    @Override
    public void checkProperties(@Nullable String name, @NotNull Map<String, String> props, @Nullable GitDeltaConsumer deltaConsumer) throws IOException, SVNException {
      final GitFile dir = getElement();
      final GitFile node = name == null ? dir : dir.getEntry(name);
      if (node == null) {
        throw new IllegalStateException("Invalid state: can't find entry " + name + " in created commit.");
      }

      if (deltaConsumer != null) {
        assert (node.getFilter() != null);
        if (deltaConsumer.migrateFilter(node.getFilter())) {
          migrateCount++;
        }
      }
    }

    public int done() throws SVNException {
      return migrateCount;
    }
  }

  private class GitPropertyValidator extends CommitAction {
    @NotNull
    private final Map<String, Set<String>> propertyMismatch = new TreeMap<>();
    private int errorCount = 0;

    public GitPropertyValidator(@NotNull GitFile root) {
      super(root);
    }

    @Override
    public void checkProperties(@Nullable String name, @NotNull Map<String, String> props, @Nullable GitDeltaConsumer deltaConsumer) throws IOException, SVNException {
      final GitFile dir = getElement();
      final GitFile node = name == null ? dir : dir.getEntry(name);
      if (node == null) {
        throw new IllegalStateException("Invalid state: can't find entry " + name + " in created commit.");
      }

      if (deltaConsumer != null) {
        assert (node.getFilter() != null);
        if (!node.getFilter().getName().equals(deltaConsumer.getFilterName())) {
          throw new IllegalStateException("Invalid writer filter:\n"
              + "Expected: " + node.getFilter().getName() + "\n"
              + "Actual: " + deltaConsumer.getFilterName());
        }
      }

      final Map<String, String> expected = node.getProperties();
      if (!props.equals(expected)) {
        if (errorCount < MAX_PROPERTY_ERRROS) {
          final StringBuilder delta = new StringBuilder();
          delta.append("Expected:\n");
          for (Map.Entry<String, String> entry : expected.entrySet()) {
            delta.append("  ").append(entry.getKey()).append(" = \"").append(entry.getValue()).append("\"\n");
          }
          delta.append("Actual:\n");
          for (Map.Entry<String, String> entry : props.entrySet()) {
            delta.append("  ").append(entry.getKey()).append(" = \"").append(entry.getValue()).append("\"\n");
          }
          propertyMismatch.compute(delta.toString(), (key, value) -> {
            if (value == null) {
              value = new TreeSet<>();
            }
            value.add(node.getFullPath());
            return value;
          });
          errorCount++;
        }
      }
    }

    public void done() throws SVNException {
      if (!propertyMismatch.isEmpty()) {
        final StringBuilder message = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : propertyMismatch.entrySet()) {
          if (message.length() > 0) {
            message.append("\n");
          }
          message.append("Invalid svn properties on files:\n");
          for (String path : entry.getValue()) {
            message.append("  ").append(path).append("\n");
          }
          message.append(entry.getKey());
        }
        message.append("\n"
            + "----------------\n" +
            "Subversion properties must be consistent with Git config files:\n");
        for (String configFile : PropertyMapping.getRegisteredFiles()) {
          message.append("  ").append(configFile).append('\n');
        }
        message.append("\n" +
            "For more detailed information you can see: ").append(WikiConstants.PROPERTIES).append("\n");
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.REPOS_HOOK_FAILURE, message.toString()));
      }
    }
  }
}
