/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.tester;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;
import svnserver.SvnTestServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider for creating tests for compare with reference svn server implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnTesterDataProvider {
  public static final class NamedFactory implements SvnTesterFactory {
    @NotNull
    private final String name;
    @NotNull
    private final SvnTesterFactory factory;

    protected NamedFactory(@NotNull String name, @NotNull SvnTesterFactory factory) {
      this.name = name;
      this.factory = factory;
    }

    @NotNull
    public SvnTester create() throws Exception {
      return factory.create();
    }

    @Override
    public String toString() {
      return name;
    }
  }

  @DataProvider
  public static Object[][] all() {
    final List<NamedFactory> testers = createTesters();
    final Object[][] result = new Object[testers.size()][];
    for (int i = 0; i < result.length; ++i) {
      result[i] = new Object[]{testers.get(i)};
    }
    return result;
  }

  public static List<NamedFactory> createTesters() {
    final List<NamedFactory> result = new ArrayList<>();
    final SvnTesterFactory external = SvnTesterExternalListener.get();
    if (external != null) {
      result.add(new NamedFactory("Native", external));
    }
    result.add(new NamedFactory("GitAsSvn", SvnTestServer::createEmpty));
    result.add(new NamedFactory("SvnKit", SvnTesterSvnKit::new));
    return result;
  }
}
