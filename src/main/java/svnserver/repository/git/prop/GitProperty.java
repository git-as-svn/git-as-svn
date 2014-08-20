package svnserver.repository.git.prop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Interface for mapping git file to subversion attributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface GitProperty {
  static final GitProperty[] emptyArray = {};

  /**
   * Update file properties on element.
   *
   * @param props Properties.
   */
  void apply(@NotNull Map<String, String> props);

  /**
   * Create GitProperty for child element.
   *
   * @param path Child node.
   * @return Child property modifier or null, if this property is not affected for childs.
   */
  @Nullable
  GitProperty createForChild(@NotNull String path);

  @NotNull
  static GitProperty[] joinProperties(@NotNull GitProperty[] parentProps, @NotNull String entryName, @NotNull GitProperty[] entryProps) {
    if (parentProps.length == 0) {
      return entryProps;
    }
    final GitProperty[] joined = new GitProperty[parentProps.length + entryProps.length];
    int index = 0;
    for (GitProperty parentProp : parentProps) {
      final GitProperty prop = parentProp.createForChild(entryName);
      if (prop != null) {
        joined[index] = prop;
        index++;
      }
    }
    System.arraycopy(entryProps, 0, joined, index, entryProps.length);
    return index == parentProps.length ? joined : Arrays.copyOf(joined, index + entryProps.length);
  }

}
