package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNNodeKind;

/**
 * Usefull methods.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitHelper {
  private GitHelper() {
  }

  public static SVNNodeKind getKind(@NotNull FileMode fileMode) {
    final int objType = fileMode.getObjectType();
    switch (objType) {
      case Constants.OBJ_TREE:
        return SVNNodeKind.DIR;
      case Constants.OBJ_BLOB:
        return SVNNodeKind.FILE;
      default:
        throw new IllegalStateException("Unknown obj type: " + objType);
    }

  }
}
