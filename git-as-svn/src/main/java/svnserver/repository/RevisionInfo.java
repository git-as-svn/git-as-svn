package svnserver.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Revision info.
 *
 * @author a.navrotskiy
 */
public interface RevisionInfo {
  @NotNull
  Map<String, String> getProperties();
}
