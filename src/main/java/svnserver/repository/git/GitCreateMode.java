/**
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Git create repository behaviour.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public enum GitCreateMode {
  ERROR {
    @NotNull
    @Override
    public Repository createRepository(@NotNull File file, @NotNull String branch) throws IOException {
      throw new FileNotFoundException(file.getPath());
    }
  },
  EMPTY {
    @NotNull
    @Override
    public Repository createRepository(@NotNull File file, @NotNull String branch) throws IOException {
      return createRepository(file);
    }
  },
  EXAMPLE {
    @NotNull
    @Override
    public Repository createRepository(@NotNull File file, @NotNull String branch) throws IOException {
      final Repository repository = createRepository(file);
      final ObjectId revision = createFirstRevision(repository);
      final RefUpdate refUpdate = repository.updateRef(Constants.R_HEADS + branch);
      refUpdate.setNewObjectId(revision);
      refUpdate.update();
      return repository;
    }
  };

  @NotNull
  public abstract Repository createRepository(@NotNull File file, @NotNull String branch) throws IOException;

  protected static Repository createRepository(@NotNull File file) throws IOException {
    if (file.exists() || file.mkdirs()) {
      final FileRepository repository = new FileRepository(file);
      repository.create(true);
      return repository;
    }
    throw new FileNotFoundException(file.getPath());
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

}
