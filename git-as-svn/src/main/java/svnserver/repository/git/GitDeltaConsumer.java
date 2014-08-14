package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import svnserver.repository.VcsDeltaConsumer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Delta consumer for applying svn diff on git blob.
 *
 * @author a.navrotskiy
 */
public class GitDeltaConsumer implements VcsDeltaConsumer {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitDeltaConsumer.class);
  private GitRepository gitRepository;
  @Deprecated
  @Nullable
  private final GitFile file;
  private final String fullPath;
  @Nullable
  private SVNDeltaProcessor window;
  @Nullable
  private final ObjectId originalId;
  @Nullable
  private ObjectId objectId;
  // todo: Wrap output stream for saving big blob to temporary files.
  @NotNull
  private ByteArrayOutputStream memory;
  @NotNull
  private FileMode fileMode;

  public GitDeltaConsumer(GitRepository gitRepository, @Nullable GitFile file, String fullPath) {
    this.gitRepository = gitRepository;
    this.file = file;
    this.fullPath = fullPath;
    this.fileMode = file != null ? file.getFileMode() : FileMode.REGULAR_FILE;
    originalId = file != null ? file.getObjectId() : null;
    objectId = originalId;
    memory = new ByteArrayOutputStream();
  }

  @Nullable
  public ObjectId getOriginalId() {
    return originalId;
  }

  @Nullable
  public ObjectId getObjectId() {
    return objectId;
  }

  @Override
  public void applyTextDelta(String path, @Nullable String baseChecksum) throws SVNException {
    try {
      if ((file != null) && (baseChecksum != null)) {
        if (!baseChecksum.equals(file.getMd5())) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
        }
      }
      if (window != null) {
        throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
      }
      window = new SVNDeltaProcessor();
      window.applyTextDelta(objectId != null ? gitRepository.openObject(objectId).openStream() : new ByteArrayInputStream(GitRepository.emptyBytes), memory, true);
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  @Override
  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    if (window == null) {
      throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
    }
    return window.textDeltaChunk(diffWindow);
  }

  @Override
  public void textDeltaEnd(String path) throws SVNException {
    try {
      if (window == null) {
        throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
      }
      objectId = gitRepository.getRepository().newObjectInserter().insert(Constants.OBJ_BLOB, memory.toByteArray());
      log.info("Created blob {} for file: {}", objectId.getName(), fullPath);
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  @Override
  public void validateChecksum(@NotNull String md5) throws SVNException {
    try {
      if (window != null) {
        if (!md5.equals(window.textDeltaEnd())) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
        }
      } else if (file != null) {
        if (!md5.equals(file.getMd5())) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH));
        }
      }
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  public FileMode getFileMode() {
    return fileMode;
  }
}
