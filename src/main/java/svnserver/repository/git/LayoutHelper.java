package svnserver.repository.git;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;

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
  public static Ref initRepository(@NotNull Repository repository) throws IOException {
    Ref ref = repository.getRef(REF);
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
          result.put(ref.getName(), revWalk.parseCommit(objectId));
        }
        if (ref.getName().startsWith(Constants.R_TAGS)) {
          final RevTag revTag = revWalk.parseTag(ref.getObjectId());
          final ObjectId objectId = revTag.getObject();
          if (revWalk.getObjectReader().has(objectId, Constants.OBJ_COMMIT)) {
            result.put(ref.getName(), revWalk.parseCommit(objectId));
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
   * @param repository Repository.
   * @param loaded     Already loaded commits.
   * @param target     Target commits.
   * @return Return new commits ordered by creation time.
   */
  public static List<RevCommit> getNewRevisions(@NotNull Repository repository, @NotNull Set<ObjectId> loaded, @NotNull Collection<ObjectId> target) {
    return null;
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

  @NotNull
  private static String revisionName(int rev) {
    return "Revision " + rev;
  }
}
