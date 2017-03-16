package svnserver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.repository.VcsAccess;

import java.io.IOException;

/**
 * Non-anonymous user.
 *
 * @author Artem V. Navrotskiy
 */
public class VcsAccessNoAnonymous implements VcsAccess {
  @Override
  public void checkRead(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    if (user.isAnonymous()) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Anonymous access not allowed"));
    }
  }

  @Override
  public void checkWrite(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    if (user.isAnonymous()) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Anonymous access not allowed"));
    }
  }
}
