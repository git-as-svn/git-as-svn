/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * DirectoryWatcher.
 *
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
final class DirectoryWatcher extends Thread {
  private final HashMap<WatchKey, Path> map = new HashMap<>();
  @NotNull
  private final Path basePath;
  private final Kind<?>[] KINDS = new Kind<?>[]{StandardWatchEventKinds.ENTRY_CREATE,
      StandardWatchEventKinds.ENTRY_DELETE};
  private final WatchService watchService;
  @NotNull
  private DirectoryMapping mapping;

  DirectoryWatcher(@NotNull String path, @NotNull DirectoryMapping mapping) {
    try {
      this.basePath = getPath(path).toAbsolutePath();
      watchService = FileSystems.getDefault().newWatchService();
      this.mapping = mapping;
      this.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Path getPath(String path) {
    return FileSystems.getDefault().getPath(path);
  }

  private static boolean isGitDirectory(Path path) {
    return Files.isDirectory(path) && path.toString().endsWith(".git");
  }

  private void addRepositories(Path parent) throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
      for (Path path : stream) {
        if (isGitDirectory(path)) {
          addRepository(parent.resolve(path));
        }
      }
    }
  }

  private void addRepository(Path path) {
    String pathName = path.getFileName().toString();
    addRepository(path.getParent().getFileName().toString(), pathName.substring(0, pathName.length() - 4));
  }

  private void addRepository(String owner, String repo) {
    mapping.addRepository(owner, repo);
  }

  private void removeRepositories(Path parent) {
    removeRepositories(parent.getFileName().toString());
  }

  private void removeRepositories(String owner) {
    mapping.removeRepositories(owner);
  }

  private void removeRepository(Path path) {
    String pathName = path.getFileName().toString();
    removeRepository(path.getParent().getFileName().toString(), pathName.substring(0, pathName.length() - 4));
  }

  private void removeRepository(String owner, String repo) {
    mapping.removeRepository(owner, repo);
  }

  public void run() {
    try {
      watchPath(basePath);

      watchSubDirectories(basePath);

      while (true) {
        WatchKey key = watchService.take();

        for (final WatchEvent<?> event : key.pollEvents()) {
          handleEvent(event, key);
        }

        if (!key.reset()) {
          boolean isBasePath = handleFailToReset(key);

          if (map.isEmpty() || isBasePath) {
            break;
          }
        }
      }

      for (WatchKey k : map.keySet()) {
        k.cancel();
      }
      map.clear();
      watchService.close();
    } catch (InterruptedException ex) {
      // noop
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void handleEvent(@NotNull WatchEvent<?> event, @NotNull WatchKey key) throws IOException {
    Path path = (Path) key.watchable();
    Path newPath = path.resolve((Path) event.context());
    boolean isBasePathKey = path.equals(basePath);
    if (isBasePathKey) {
      Kind<?> kind = event.kind();
      if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
        watchPath(newPath);
        addRepositories(newPath);
      } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
        // Remove key relating to old path name

        for (Iterator<Entry<WatchKey, Path>> it = map.entrySet().iterator(); it.hasNext(); ) {
          Entry<WatchKey, Path> e = it.next();
          WatchKey eKey = e.getKey();
          Path ePath = e.getValue();
          if (ePath.equals(newPath)) {
            eKey.cancel();
            it.remove();
          }
        }
        removeRepositories(newPath);
      }
    } else if (isGitDirectory(newPath)) {
      Kind<?> kind = event.kind();
      if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
        addRepository(newPath);
      } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
        removeRepository(newPath);
      }
    }
  }

  private boolean handleFailToReset(@NotNull WatchKey key) {
    Path path = (Path) key.watchable();
    key.cancel();
    for (Iterator<Entry<WatchKey, Path>> it = map.entrySet().iterator(); it.hasNext(); ) {
      Entry<WatchKey, Path> e = it.next();
      WatchKey eKey = e.getKey();
      Path ePath = e.getValue();
      if (ePath.getParent().equals(path)) {
        eKey.cancel();
        it.remove();
      }
    }
    return path.equals(basePath);
  }

  @NotNull
  private WatchKey watchPath(@NotNull Path path) throws IOException {
    WatchKey key = path.register(watchService, KINDS);
    map.put(key, path);
    return key;
  }

  private void watchSubDirectories(@NotNull Path path) throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
      for (Path directory : stream) {
        if (Files.isDirectory(directory)) {
          watchPath(path.resolve(directory).toAbsolutePath());
          addRepositories(directory);
        }
      }
    }
  }

  public interface DirectoryMapping {
    void addRepository(@NotNull String owner, @NotNull String repo);

    void removeRepositories(@NotNull String owner);

    void removeRepository(@NotNull String owner, @NotNull String repo);

  }
}
