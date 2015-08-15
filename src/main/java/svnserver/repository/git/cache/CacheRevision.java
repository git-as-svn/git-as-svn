/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Revision cache information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class CacheRevision {
  @NotNull
  public static final CacheRevision empty = new CacheRevision();
  @NotNull
  private static final Kryo kryo = createKryo();

  @Nullable
  private final ObjectId gitCommitId;
  @NotNull
  private final Map<String, String> renames = new TreeMap<>();
  @NotNull
  private final Map<String, CacheChange> fileChange = new TreeMap<>();

  protected CacheRevision() {
    this.gitCommitId = null;
  }

  public CacheRevision(
      @Nullable RevCommit svnCommit,
      @NotNull Map<String, String> renames,
      @NotNull Map<String, CacheChange> fileChange
  ) {
    if (svnCommit != null) {
      this.gitCommitId = svnCommit.copy();
    } else {
      this.gitCommitId = null;
    }
    this.renames.putAll(renames);
    this.fileChange.putAll(fileChange);
  }

  @Nullable
  public ObjectId getGitCommitId() {
    return gitCommitId;
  }

  @NotNull
  public Map<String, String> getRenames() {
    return Collections.unmodifiableMap(renames);
  }

  @NotNull
  public Map<String, CacheChange> getFileChange() {
    return Collections.unmodifiableMap(fileChange);
  }

  private static Kryo createKryo() {
    final Kryo kryo = new Kryo();
    kryo.register(ObjectId.class, new Serializer<ObjectId>() {

      @Override
      public void write(@NotNull Kryo kryo, @NotNull Output output, @Nullable ObjectId object) {
        output.writeString(object != null ? object.name() : null);
      }

      @Override
      public ObjectId read(Kryo kryo, Input input, Class<ObjectId> type) {
        final String id = input.readString();
        return id != null ? ObjectId.fromString(id) : null;
      }
    });
    return kryo;
  }

  @Nullable
  public static CacheRevision deserialize(@Nullable byte[] bytes) {
    if (bytes != null) {
      try (final Input input = new Input(bytes)) {
        return kryo.readObjectOrNull(input, CacheRevision.class);
      } catch (KryoException ignored) {
        return null;
      }
    }
    return null;
  }

  @NotNull
  public static byte[] serialize(@NotNull CacheRevision cache) throws IOException {
    try (final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      try (Output output = new Output(stream)) {
        kryo.writeObject(output, cache);
      }
      try (final Input input = new Input(stream.toByteArray())) {
        kryo.readObjectOrNull(input, CacheRevision.class);
      } catch (KryoException e) {
        throw new IOException("Can't read serialized object", e);
      }
      return stream.toByteArray();
    }
  }
}
