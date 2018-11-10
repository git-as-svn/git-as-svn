/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.keys;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.WatchEvent.Kind;
import java.util.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import svnserver.context.Shared;

/**
 * SSHDirectoryWatcher.
 *
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
public class SSHDirectoryWatcher extends Thread implements Shared {
  private static final Kind<?>[] KINDS = new Kind<?>[] { StandardWatchEventKinds.ENTRY_CREATE,
      StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE };
  private static final String AUTHORIZED_KEYS = "authorized_keys";

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SSHDirectoryWatcher.class);

  @NotNull
  private Path basePath;
  @NotNull
  private Path realSSHPath;
  @NotNull
  private String originalAppPath;
  @NotNull
  private String svnServePath;
  @NotNull
  private final WatchService watchService;
  @Nullable
  private final KeysMapper mapper;

  @NotNull
  private static Path getPath(@NotNull String path) {
    return FileSystems.getDefault().getPath(path);
  }

  public SSHDirectoryWatcher(@NotNull KeysConfig config, @Nullable KeysMapper mapper) {
    this.originalAppPath = config.getOriginalAppPath();
    this.svnServePath = config.getSvnservePath();
    this.mapper = mapper;
    try {
      this.basePath = getPath(config.getShadowSSHDirectory()).toAbsolutePath();
      this.realSSHPath = getPath(config.getRealSSHDirectory()).toAbsolutePath();
      this.watchService = FileSystems.getDefault().newWatchService();
      this.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void run() {
    try {
      // Run this first.
      mungeAuthorizedKeys();
      basePath.register(watchService, KINDS);
      while (!isInterrupted()) {
        WatchKey key = watchService.take();
        if (isInterrupted()) {
          break;
        }
        for (final WatchEvent<?> event : key.pollEvents()) {
          Object context = event.context();
          if (!(context instanceof Path)) {
            continue;
          }
          Path p = (Path) context;
          if (!p.toString().equals(AUTHORIZED_KEYS)) {
            continue;
          }
          // OK we're looking at authorized_keys - munge it!
          mungeAuthorizedKeys();
        }

        if (!key.reset()) {
          key.cancel();
          break;
        }
      }
    } catch (InterruptedException e) {
      return;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void mungeAuthorizedKeys() throws IOException {
    Path authPath = basePath.resolve(AUTHORIZED_KEYS);
    Path realAuthPath = realSSHPath.resolve(AUTHORIZED_KEYS);
    log.info("Processing the authorized_keys file: {}", authPath.toString());

    HashSet<String> keysSet = new HashSet<>();

    try (
      BufferedReader reader = Files.newBufferedReader(authPath);
      BufferedWriter writer = Files.newBufferedWriter(realAuthPath);
    ) {
      reader.lines().map(s -> {
        if (s.indexOf(originalAppPath) > -1) {
          int indexOfKey = s.indexOf("key-");
          keysSet.add(s.substring(indexOfKey, s.indexOf(' ', indexOfKey)));
          return s.replace(originalAppPath, svnServePath);
        } else {
          return s;
        }
      }).forEach(s -> {
        try {
          writer.write(s);
          writer.write('\n');
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }

    log.info("Found {} keys", keysSet.size());

    // OK now we know about which keys are there.
    // So we tell our keys mapper...
    if (this.mapper != null) {
      this.mapper.setKeys(keysSet);
    }
  }

  public void close() {
    this.interrupt();
  }
}