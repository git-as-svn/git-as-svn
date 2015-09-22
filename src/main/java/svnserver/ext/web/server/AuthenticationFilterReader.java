/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.jetbrains.annotations.NotNull;
import svnserver.context.LocalContext;
import svnserver.ext.web.annotations.SecureReader;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.ext.Provider;

/**
 * Check read access.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@Priority(Priorities.AUTHENTICATION)
@Provider
@SecureReader
public class AuthenticationFilterReader extends AuthenticationFilter {
  public AuthenticationFilterReader(@NotNull LocalContext context) {
    super(context, (access, user) -> access.checkRead(user, null));
  }
}
