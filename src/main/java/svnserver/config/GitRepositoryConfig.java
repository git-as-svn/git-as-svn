/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.filter.LfsFilter;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;
import svnserver.repository.git.GitCreateMode;
import svnserver.repository.git.GitLocation;
import svnserver.repository.git.GitRepository;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.filter.GitFilterGzip;
import svnserver.repository.git.filter.GitFilterLink;
import svnserver.repository.git.filter.GitFilterRaw;
import svnserver.repository.git.push.GitPushEmbeddedConfig;
import svnserver.repository.git.push.GitPusher;
import svnserver.repository.locks.LocalLockManager;
import svnserver.repository.locks.LockStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
public final class GitRepositoryConfig {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepositoryConfig.class);
  @NotNull
  private Set<String> branches = new TreeSet<>();
  @NotNull
  private String path = ".git";
  @NotNull
  private GitPusherConfig pusher = GitPushEmbeddedConfig.instance;
  @NotNull
  private GitCreateMode createMode;
  private boolean renameDetection = true;

  public GitRepositoryConfig() {
    this(GitCreateMode.ERROR);
  }

  public GitRepositoryConfig(@NotNull GitCreateMode createMode) {
    this(createMode, Collections.singletonList(Constants.MASTER));
  }

  public GitRepositoryConfig(@NotNull GitCreateMode createMode, @NotNull List<String> branches) {
    this.createMode = createMode;
    this.branches.addAll(branches);
  }

  @NotNull
  public String getPath() {
    return path;
  }

  @NotNull
  public GitRepository create(@NotNull LocalContext context) throws IOException {
    return create(context, ConfigHelper.joinPath(context.getShared().getBasePath(), path));
  }

  @NotNull
  public GitRepository create(@NotNull LocalContext context, @NotNull File fullPath) throws IOException {
    context.add(GitLocation.class, new GitLocation(fullPath));

    final LfsStorage lfsStorage = LfsStorageFactory.tryCreateStorage(context);
    final Repository git = createRepository(context, fullPath);

    return createRepository(context, lfsStorage, git, pusher.create(context), branches, renameDetection);
  }

  @NotNull
  private Repository createRepository(@NotNull LocalContext context, @NotNull File fullPath) throws IOException {
    if (!fullPath.exists()) {
      log.info("[{}]: storage {} not found, create mode: {}", context.getName(), fullPath, createMode);
      return createMode.createRepository(fullPath, branches);
    }
    log.info("[{}]: using existing storage {}", context.getName(), fullPath);
    return new FileRepository(fullPath);
  }

  @NotNull
  public static GitRepository createRepository(@NotNull LocalContext context, @Nullable LfsStorage lfsStorage, @NotNull Repository git, @NotNull GitPusher pusher, @NotNull Set<String> branches, boolean renameDetection) throws IOException {
    final LockStorage lockStorage;
    if (lfsStorage != null) {
      context.add(LfsStorage.class, lfsStorage);
      lockStorage = lfsStorage;
    } else {
      lockStorage = new LocalLockManager(LocalLockManager.getPersistentStorage(context));
    }

    final Map<String, GitFilter> filters = new HashMap<>();

    filters.put(GitFilterLink.NAME, new GitFilterLink(context));
    filters.put(GitFilterGzip.NAME, new GitFilterGzip(context));
    filters.put(GitFilterRaw.NAME, new GitFilterRaw(context));
    filters.put(LfsFilter.NAME, new LfsFilter(context, lfsStorage));

    return new GitRepository(context, git, pusher, branches, renameDetection, lockStorage, filters);
  }
}
