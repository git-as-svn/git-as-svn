package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.repository.VcsFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFile implements VcsFile {
  @NotNull
  private final GitRepository repo;
  @NotNull
  private final ObjectId objectId;
  @NotNull
  private final FileMode fileMode;
  @NotNull
  private final String fileName;
  @NotNull
  private final GitRevision lastChange;
  @Nullable
  private ObjectLoader objectLoader;

  public GitFile(@NotNull GitRepository repo, @NotNull ObjectId objectId, @NotNull FileMode fileMode, @NotNull String fileName, @NotNull GitRevision lastChange) {
    this.repo = repo;
    this.objectId = objectId;
    this.fileMode = fileMode;
    this.fileName = fileName;
    this.lastChange = lastChange;
  }

  @NotNull
  @Override
  public String getFileName() {
    return fileName;
  }

  @NotNull
  public FileMode getFileMode() {
    return fileMode;
  }

  @NotNull
  public ObjectId getObjectId() {
    return objectId;
  }

  @NotNull
  @Override
  public Map<String, String> getProperties(boolean includeInternalProps) {
    final Map<String, String> props = new HashMap<>();
    if (fileMode.equals(FileMode.EXECUTABLE_FILE)) {
      props.put(SVNProperty.EXECUTABLE, "*");
    } else if (fileMode.equals(FileMode.SYMLINK)) {
      props.put(SVNProperty.SPECIAL, "*");
    }
    if (includeInternalProps) {
      props.put(SVNProperty.UUID, repo.getUuid());
      props.put(SVNProperty.COMMITTED_REVISION, String.valueOf(lastChange.getId()));
      props.put(SVNProperty.COMMITTED_DATE, lastChange.getDate());
      props.put(SVNProperty.LAST_AUTHOR, lastChange.getAuthor());
    }
    return props;
  }

  @NotNull
  @Override
  public String getMd5() throws IOException {
    return repo.getObjectMD5(objectId);
  }

  @Override
  public long getSize() throws IOException {
    return getObjectLoader().getSize();
  }

  @Override
  public boolean isDirectory() throws IOException {
    return getKind().equals(SVNNodeKind.DIR);
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() throws IOException {
    final int objType = fileMode.getObjectType();

    switch (objType) {
      case Constants.OBJ_TREE:
        return SVNNodeKind.DIR;
      case Constants.OBJ_BLOB:
        return SVNNodeKind.FILE;
      default:
        throw new IllegalStateException("Unknown obj type: " + objType);
    }
  }

  private ObjectLoader getObjectLoader() throws IOException {
    if (objectLoader == null) {
      objectLoader = repo.openObject(objectId);
    }
    return objectLoader;
  }

  @Override
  public void copyTo(@NotNull OutputStream stream) throws IOException {
    getObjectLoader().copyTo(stream);
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    return getObjectLoader().openStream();
  }

  @NotNull
  @Override
  public Iterable<VcsFile> getEntries() throws IOException {
    final CanonicalTreeParser treeParser = new CanonicalTreeParser(GitRepository.emptyBytes, repo.getRepository().newObjectReader(), objectId);
    return () -> new Iterator<VcsFile>() {
      @Override
      public boolean hasNext() {
        return !treeParser.eof();
      }

      @Override
      public VcsFile next() {
        final GitFile fileInfo = new GitFile(repo, treeParser.getEntryObjectId(), treeParser.getEntryFileMode(), treeParser.getEntryPathString(), lastChange);
        treeParser.next();
        return fileInfo;
      }
    };
  }

  @NotNull
  @Override
  public GitRevision getLastChange() {
    return lastChange;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitFile that = (GitFile) o;
    return fileMode.equals(that.fileMode)
        && fileName.equals(that.fileName)
        && objectId.equals(that.objectId);
  }

  @Override
  public int hashCode() {
    int result = objectId.hashCode();
    result = 31 * result + fileMode.hashCode();
    result = 31 * result + fileName.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "GitFileInfo{" +
        "fileName='" + fileName + '\'' +
        ", objectId=" + objectId +
        '}';
  }
}
