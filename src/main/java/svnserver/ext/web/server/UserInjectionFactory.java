/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.glassfish.hk2.api.Factory;
import org.jetbrains.annotations.NotNull;
import svnserver.auth.User;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;

/**
 * User injection factory.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class UserInjectionFactory implements Factory<User> {
  @NotNull
  private final User user;

  @Inject
  public UserInjectionFactory(ContainerRequestContext requestContext) {
    this.user = (User) requestContext.getProperty(User.class.getName());
  }

  @Override
  public User provide() {
    return user;
  }

  @Override
  public void dispose(User instance) {
  }
}
