/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import org.mapdb.serializer.GroupSerializerObjectArray;
import svnserver.repository.git.cache.CacheChange;
import svnserver.repository.git.cache.CacheRevision;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

final class CacheRevisionSerializer extends GroupSerializerObjectArray<CacheRevision> {

  @NotNull
  static final CacheRevisionSerializer instance = new CacheRevisionSerializer();

  @Override
  public void serialize(@NotNull DataOutput2 out, @NotNull CacheRevision value) throws IOException {
    final ObjectId objectId = value.getGitCommitId();
    out.writeBoolean(objectId != null);
    if (objectId != null)
      ObjectIdSerializer.instance.serialize(out, objectId);

    out.writeInt(value.getRenames().size());
    for (Map.Entry<String, String> en : value.getRenames().entrySet()) {
      Serializer.STRING.serialize(out, en.getKey());
      Serializer.STRING.serialize(out, en.getValue());
    }

    out.writeInt(value.getFileChange().size());
    for (Map.Entry<String, CacheChange> en : value.getFileChange().entrySet()) {
      Serializer.STRING.serialize(out, en.getKey());

      final ObjectId oldFile = en.getValue().getOldFile();
      out.writeBoolean(oldFile != null);
      if (oldFile != null)
        ObjectIdSerializer.instance.serialize(out, oldFile);

      final ObjectId newFile = en.getValue().getNewFile();
      out.writeBoolean(newFile != null);
      if (newFile != null)
        ObjectIdSerializer.instance.serialize(out, newFile);
    }
  }

  @Override
  public CacheRevision deserialize(@NotNull DataInput2 input, int available) throws IOException {
    final ObjectId objectId = input.readBoolean() ? ObjectIdSerializer.instance.deserialize(input, available) : null;

    final Map<String, String> renames = new TreeMap<>();
    final int renamesCount = input.readInt();
    for (int i = 0; i < renamesCount; ++i) {
      renames.put(Serializer.STRING.deserialize(input, available), Serializer.STRING.deserialize(input, available));
    }

    final Map<String, CacheChange> fileChange = new TreeMap<>();
    final int fileChangeCount = input.readInt();
    for (int i = 0; i < fileChangeCount; ++i) {
      final String name = Serializer.STRING.deserialize(input, available);
      final ObjectId oldFile = input.readBoolean() ? ObjectIdSerializer.instance.deserialize(input, available) : null;
      final ObjectId newFile = input.readBoolean() ? ObjectIdSerializer.instance.deserialize(input, available) : null;
      fileChange.put(name, new CacheChange(oldFile, newFile));
    }

    return new CacheRevision(objectId, renames, fileChange);
  }
}
