package svnserver.repository.git.prop;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Interface for mapping git file to subversion attributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface GitProperty {
  static final GitProperty[] emptyArray = {};

  void apply(@NotNull Map<String, String> props);
}
