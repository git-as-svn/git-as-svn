package svnserver.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Информация о директории.
 *
 * @author a.navrotskiy
 */
@Deprecated
public interface VcsDirectoryConsumer {
  @NotNull
  String getPath();

  /**
   * Properties of copying/modifing node.
   *
   * @return Properties.
   */
  @NotNull
  Map<String, String> getProperties();
}
