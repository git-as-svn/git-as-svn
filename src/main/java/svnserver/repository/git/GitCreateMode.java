/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Git create repository behaviour.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public enum GitCreateMode {
  ERROR {
    @NotNull
    @Override
    public Repository createRepository(@NotNull Path path, @NotNull Set<String> branches) throws IOException {
      throw new FileNotFoundException(path.toString());
    }
  },
  EMPTY {
    @NotNull
    @Override
    public Repository createRepository(@NotNull Path path, @NotNull Set<String> branches) throws IOException {
      return createRepository(path);
    }
  },
  EXAMPLE {
    @NotNull
    @Override
    public Repository createRepository(@NotNull Path path, @NotNull Set<String> branches) throws IOException {
      final Repository repository = createRepository(path);
      final ObjectId revision = createFirstRevision(repository);
      for (String branch : branches) {
        final RefUpdate refUpdate = repository.updateRef(Constants.R_HEADS + branch);
        refUpdate.setNewObjectId(revision);
        refUpdate.update();
      }
      return repository;
    }
  };

  @NotNull
  protected static Repository createRepository(@NotNull Path path) throws IOException {
    final FileRepository repository = new FileRepository(Files.createDirectories(path).toFile());
    repository.create(true);
    return repository;
  }

  @NotNull
  private static ObjectId createFirstRevision(@NotNull Repository repository) throws IOException {
    final ObjectInserter inserter = repository.newObjectInserter();
    // Create commit tree.
    final TreeFormatter rootBuilder = new TreeFormatter();
    rootBuilder.append(".gitattributes", FileMode.REGULAR_FILE, insertFile(inserter, "example/_gitattributes"));
    new ObjectChecker().checkTree(rootBuilder.toByteArray());
    final ObjectId rootId = inserter.insert(rootBuilder);
    // Create first commit with message.
    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(new PersonIdent("", "", 0, 0));
    commitBuilder.setCommitter(new PersonIdent("", "", 0, 0));
    commitBuilder.setMessage("Initial commit");
    commitBuilder.setTreeId(rootId);
    final ObjectId commitId = inserter.insert(commitBuilder);
    inserter.flush();
    return commitId;
  }

  @NotNull
  private static AnyObjectId insertFile(@NotNull ObjectInserter inserter, @NotNull String resourceName) throws IOException {
    final InputStream stream = GitCreateMode.class.getResourceAsStream(resourceName);
    if (stream == null) {
      throw new FileNotFoundException(resourceName);
    }
    return inserter.insert(Constants.OBJ_BLOB, IOUtils.toByteArray(stream));
  }

  @NotNull
  public abstract Repository createRepository(@NotNull Path path, @NotNull Set<String> branches) throws IOException;
}
