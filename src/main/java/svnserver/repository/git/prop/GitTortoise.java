/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.eclipse.jgit.lib.FileMode;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parse and processing .tgitconfig.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitTortoise implements GitProperty {

  @SuppressWarnings("UnusedDeclaration")
  public static final class Factory implements GitPropertyFactory {
    @NotNull
    @Override
    public String getFileName() {
      return ".tgitconfig";
    }

    @NotNull
    @Override
    public GitProperty[] create(@NotNull InputStream stream) throws IOException {
      return new GitProperty[]{
          parseConfig(stream)
      };
    }
  }

  @NotNull
  private final Map<String, String> tortoiseProps;

  private GitTortoise(@NotNull Map<String, String> tortoiseProps) {
    this.tortoiseProps = tortoiseProps;
  }

  @NotNull
  static GitTortoise parseConfig(@NotNull InputStream stream) throws IOException {
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") final Ini ini = new Ini(stream);
    final Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, Profile.Section> sectionEntry : ini.entrySet()) {
      for (Map.Entry<String, String> configEntry : sectionEntry.getValue().entrySet()) {
        String value = configEntry.getValue();
        if (value.startsWith("\"") && value.endsWith("\"")) {
          value = value.substring(1, value.length() - 1);
        }
        result.put(sectionEntry.getKey() + ":" + configEntry.getKey(), value);
      }
    }
    return new GitTortoise(result.isEmpty() ? Collections.emptyMap() : result);
  }

  @Override
  public void apply(@NotNull Map<String, String> props) {
    props.putAll(tortoiseProps);
  }

  @Nullable
  @Override
  public String getFilterName() {
    return null;
  }

  @Nullable
  @Override
  public GitProperty createForChild(@NotNull String name, @NotNull FileMode fileMode) {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final GitTortoise that = (GitTortoise) o;
    return tortoiseProps.equals(that.tortoiseProps);
  }

  @Override
  public int hashCode() {
    return tortoiseProps.hashCode();
  }
}
