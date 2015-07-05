/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import svnserver.TemporaryOutputStream;
import svnserver.repository.VcsDeltaConsumer;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.prop.GitProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Delta consumer for applying svn diff on git blob.
 *
 * @author a.navrotskiy
 */
public class GitDeltaConsumer implements VcsDeltaConsumer {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitDeltaConsumer.class);
  @NotNull
  private final Map<String, String> props;
  @NotNull
  private final GitRepository gitRepository;
  @Nullable
  private SVNDeltaProcessor window;
  @Nullable
  private final GitObject<ObjectId> originalId;
  @Nullable
  private final String originalMd5;
  @Nullable
  private GitObject<ObjectId> objectId;
  @Nullable
  private final GitFilter oldFilter;
  @Nullable
  private GitFilter newFilter;

  // todo: Wrap output stream for saving big blob to temporary files.
  @NotNull
  private TemporaryOutputStream temporaryStream;

  public GitDeltaConsumer(@NotNull GitRepository gitRepository, @Nullable GitFile file) throws IOException, SVNException {
    this.gitRepository = gitRepository;
    if (file != null) {
      this.originalMd5 = file.getMd5();
      this.originalId = file.getObjectId();
      this.props = new HashMap<>(file.getProperties());
      this.oldFilter = file.getFilter();
    } else {
      this.originalMd5 = null;
      this.originalId = null;
      this.props = new HashMap<>();
      this.oldFilter = null;
    }
    this.newFilter = null;
    this.objectId = originalId;
    this.temporaryStream = new TemporaryOutputStream();
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() {
    return props;
  }

  @Nullable
  public GitObject<ObjectId> getOriginalId() {
    return originalId;
  }

  @Nullable
  public GitObject<ObjectId> getObjectId() throws IOException, SVNException {
    if ((originalId != null) && originalId.equals(objectId) && (newFilter == null)) {
      this.
          newFilter = gitRepository.getFilter(props.containsKey(SVNProperty.SPECIAL) ? FileMode.SYMLINK : FileMode.REGULAR_FILE, GitProperty.emptyArray);
      if (oldFilter == null) {
        throw new IllegalStateException("Original object ID defined, but original Filter is not defined");
      }
      if (!newFilter.equals(oldFilter)) {
        final Repository repo = gitRepository.getRepository();

        final TemporaryOutputStream content = new TemporaryOutputStream();
        try (InputStream inputStream = oldFilter.inputStream(originalId);
             OutputStream outputStream = newFilter.outputStream(content)) {
          IOUtils.copy(inputStream, outputStream);
        }

        final ObjectInserter inserter = repo.newObjectInserter();
        try (InputStream inputStream = content.toInputStream()) {
          objectId = new GitObject<>(repo, inserter.insert(Constants.OBJ_BLOB, content.size(), inputStream));
        }
        inserter.flush();
      }
    }
    return objectId;
  }

  @Override
  public void applyTextDelta(String path, @Nullable String baseChecksum) throws SVNException {
    try {
      if ((originalMd5 != null) && (baseChecksum != null)) {
        if (!baseChecksum.equals(originalMd5)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
        }
      }

      if (window != null)
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CMD_ERR));

      // todo #72: Need correct new filter calculation. In this case file will write as is and after that rewrite with correct filter.
      newFilter = gitRepository.getFilter(props.containsKey(SVNProperty.SPECIAL) ? FileMode.SYMLINK : FileMode.REGULAR_FILE, GitProperty.emptyArray);
      window = new SVNDeltaProcessor();
      window.applyTextDelta(objectId != null ? objectId.openObject().openStream() : new ByteArrayInputStream(GitRepository.emptyBytes), newFilter.outputStream(temporaryStream), true);
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  @Override
  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    if (window == null)
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CMD_ERR));

    return window.textDeltaChunk(diffWindow);
  }

  @Override
  public void textDeltaEnd(String path) throws SVNException {
    try {
      if (window == null)
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CMD_ERR));

      final Repository repo = gitRepository.getRepository();
      final ObjectInserter inserter = repo.newObjectInserter();
      try (InputStream stream = temporaryStream.toInputStream()) {
        objectId = new GitObject<>(repo, inserter.insert(Constants.OBJ_BLOB, temporaryStream.size(), stream));
      }
      inserter.flush();
      log.info("Created blob {} for file: {}", objectId.getObject().getName(), path);
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  @Override
  public void validateChecksum(@NotNull String md5) throws SVNException {
    if (window != null) {
      if (!md5.equals(window.textDeltaEnd())) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
      }
    } else if (originalMd5 != null) {
      if (!originalMd5.equals(md5)) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
      }
    }
  }

  @NotNull
  public String getFilterName() {
    if (newFilter != null)
      return newFilter.getName();
    if (oldFilter != null)
      return oldFilter.getName();
    throw new IllegalStateException();
  }
}
