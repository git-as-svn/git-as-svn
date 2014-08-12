package svnserver.repository.git;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.repository.Repository;
import svnserver.repository.RevisionInfo;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Implementation for Git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitRepository implements Repository {
  @NotNull
  private final FileRepository repository;
  @NotNull
  private final List<RevCommit> revisions;

  public GitRepository() throws IOException {
    repository = new FileRepository(findGitPath());
    revisions = loadRevisions(repository);
  }

  private static List<RevCommit> loadRevisions(@NotNull FileRepository repository) throws IOException {
    final Ref master = repository.getRef("master");
    final LinkedList<RevCommit> revisions = new LinkedList<>();
    final RevWalk revWalk = new RevWalk(repository);
    ObjectId objectId = master.getObjectId();
    while (true) {
      final RevCommit commit = revWalk.parseCommit(objectId);
      revisions.addFirst(commit);
      if (commit.getParentCount() == 0) break;
      objectId = commit.getParent(0);
    }
    return new ArrayList<>(revisions);
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
    return revisions.size();
  }

  @Override
  public RevisionInfo getRevisionInfo(int revision) throws IOException {
    final RevCommit commit = getRevision(revision);
    Map<String, String> props = new HashMap<>();
    props.put(SvnConstants.PROP_AUTHOR, commit.getCommitterIdent().getName());
    props.put(SvnConstants.PROP_LOG, commit.getFullMessage().trim());
    props.put(SvnConstants.PROP_DATE, StringHelper.formatDate(TimeUnit.SECONDS.toMillis(commit.getCommitTime())));
    props.put(SvnConstants.PROP_GIT, commit.name());
    return new RevisionInfo() {
      @Override
      public int getId() {
        return revision;
      }

      @NotNull
      @Override
      public Map<String, String> getProperties() {
        return props;
      }

      @Nullable
      @Override
      public ObjectLoader getFile(@NotNull String fullPath) throws IOException {
        final TreeWalk treeWalk = TreeWalk.forPath(repository, fullPath.substring(1), commit.getTree());
        if (treeWalk == null) {
          return null;
        }
        if (treeWalk.getFileMode(0).equals(FileMode.TREE)) {
          return null;
        }
        return repository.newObjectReader().open(treeWalk.getObjectId(0));
      }
    };
  }

  @NotNull
  private RevCommit getRevision(int revision) {
    return revisions.get(revision - 1);
  }
}
