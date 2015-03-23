/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Common test functions.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class TestHelper {
  public static void saveFile(@NotNull File file, @NotNull String content) throws IOException {
    try (OutputStream stream = new FileOutputStream(file)) {
      stream.write(content.getBytes(StandardCharsets.UTF_8));
    }
  }

  public static File findGitPath() {
    final File root = new File(".").getAbsoluteFile();
    File path = root;
    while (true) {
      final File repo = new File(path, ".git");
      if (repo.exists()) {
        return repo;
      }
      path = path.getParentFile();
      if (path == null) {
        throw new IllegalStateException("Repository not found from directiry: " + root.getAbsolutePath());
      }
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static File createTempDir(@NotNull String prefix) throws IOException {
    final File tmp = new File(findGitPath().getParentFile(), "build/tmp/");
    tmp.mkdirs();
    final File dir = File.createTempFile(prefix + "-", "", tmp);
    dir.delete();
    dir.mkdir();
    return dir;
  }

  public static void deleteDirectory(@NotNull File file) throws IOException {
    if (file.isDirectory()) {
      final File[] files = file.listFiles();
      if (files != null) {
        for (File entry : files) {
          deleteDirectory(entry);
        }
      }
    }
    if (!file.delete()) {
      throw new FileNotFoundException("Failed to delete file: " + file);
    }
  }

  public static Repository emptyRepository() throws IOException {
    final Repository repository = new InMemoryRepository(new DfsRepositoryDescription(null));
    repository.create();
    return repository;
  }
}
