/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.CharSequenceInputStream;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Common test functions.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class TestHelper {

  @NotNull
  public static final Logger logger = LoggerFactory.getLogger("test");

  public static void saveFile(@NotNull Path file, @NotNull String content) throws IOException {
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  @NotNull
  public static Path createTempDir(@NotNull String prefix) throws IOException {
    return Files.createTempDirectory(findGitPath().getParent().resolve("build/tmp"), prefix + "-").toAbsolutePath();
  }

  @NotNull
  static Path findGitPath() {
    final Path root = Paths.get("").toAbsolutePath();
    Path path = root;
    while (true) {
      final Path repo = path.resolve(".git");
      if (Files.isDirectory(repo))
        return repo;

      path = path.getParent();
      if (path == null)
        throw new IllegalStateException("Repository not found from directiry: " + root);
    }
  }

  public static void deleteDirectory(@NotNull Path file) throws IOException {
    FileUtils.deleteDirectory(file.toFile());
  }

  @NotNull
  public static InputStream asStream(@NotNull String content) {
    return new CharSequenceInputStream(content, StandardCharsets.UTF_8);
  }

  @NotNull
  static Repository emptyRepository() throws IOException {
    final Repository repository = new InMemoryRepository(new DfsRepositoryDescription(null));
    repository.create();
    return repository;
  }
}
