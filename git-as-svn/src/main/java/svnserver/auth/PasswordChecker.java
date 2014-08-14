package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@FunctionalInterface
public interface PasswordChecker {
  @Nullable
  User check(@NotNull String username, @NotNull String password) throws SVNException;
}
