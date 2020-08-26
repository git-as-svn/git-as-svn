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
import svnserver.Loggers;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;
import svnserver.repository.git.EmptyDirsSupport;
import svnserver.repository.git.GitCreateMode;
import svnserver.repository.git.GitLocation;
import svnserver.repository.git.GitRepository;
import svnserver.repository.git.filter.GitFilters;
import svnserver.repository.git.push.GitPushEmbeddedConfig;
import svnserver.repository.git.push.GitPusher;
import svnserver.repository.locks.LocalLockManager;
import svnserver.repository.locks.LockStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
public final class GitRepositoryConfig {
  @NotNull
  private static final Logger log = Loggers.git;
  @NotNull
  private final Set<String> branches = new TreeSet<>();
  @NotNull
  private final String path = ".git";
  @NotNull
  private final GitPusherConfig pusher = GitPushEmbeddedConfig.instance;
  @NotNull
  private final GitCreateMode createMode;
  private final boolean renameDetection = true;
  @NotNull
  private final EmptyDirsSupport emptyDirs = EmptyDirsSupport.Disabled;

  public GitRepositoryConfig() {
    this(GitCreateMode.ERROR);
  }

  public GitRepositoryConfig(@NotNull GitCreateMode createMode) {
    this(createMode, Collections.singletonList(Constants.MASTER));
  }

  private GitRepositoryConfig(@NotNull GitCreateMode createMode, @NotNull List<String> branches) {
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
  public GitRepository create(@NotNull LocalContext context, @NotNull Path fullPath) throws IOException {
    return create(context, fullPath, branches);
  }

  @NotNull
  public GitRepository create(@NotNull LocalContext context, @NotNull Path fullPath, @NotNull Set<String> branches) throws IOException {
    context.add(GitLocation.class, new GitLocation(fullPath));

    final LfsStorage lfsStorage = LfsStorageFactory.tryCreateStorage(context);
    final Repository git = createGit(context, fullPath);

    return createRepository(context, lfsStorage, git, pusher.create(context), branches, renameDetection, emptyDirs);
  }

  @NotNull
  private Repository createGit(@NotNull LocalContext context, @NotNull Path fullPath) throws IOException {
    if (!Files.exists(fullPath)) {
      log.info("[{}]: storage {} not found, create mode: {}", context.getName(), fullPath, createMode);
      return createMode.createRepository(fullPath, branches);
    }
    log.info("[{}]: using existing storage {}", context.getName(), fullPath);
    return new FileRepository(fullPath.toFile());
  }

  @NotNull
  public static GitRepository createRepository(@NotNull LocalContext context,
                                               @Nullable LfsStorage lfsStorage,
                                               @NotNull Repository git,
                                               @NotNull GitPusher pusher,
                                               @NotNull Set<String> branches,
                                               boolean renameDetection,
                                               @NotNull EmptyDirsSupport emptyDirs) throws IOException {
    final LockStorage lockStorage;
    if (lfsStorage != null) {
      context.add(LfsStorage.class, lfsStorage);
      lockStorage = lfsStorage;
    } else {
      lockStorage = new LocalLockManager(LocalLockManager.getPersistentStorage(context));
    }

    final GitFilters filters = new GitFilters(context, lfsStorage);
    return new GitRepository(context, git, pusher, branches, renameDetection, lockStorage, filters, emptyDirs);
  }

}
