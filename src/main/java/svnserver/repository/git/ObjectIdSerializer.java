/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

import java.io.IOException;

final class ObjectIdSerializer extends GroupSerializerObjectArray<ObjectId> {
  @NotNull
  static final ObjectIdSerializer instance = new ObjectIdSerializer();

  @Override
  public void serialize(@NotNull DataOutput2 out, @NotNull ObjectId value) throws IOException {
    value.copyRawTo(out);
  }

  @Override
  public ObjectId deserialize(@NotNull DataInput2 input, int available) throws IOException {
    final byte[] raw = new byte[fixedSize()];
    input.readFully(raw);
    return ObjectId.fromRaw(raw);
  }

  @Override
  public int fixedSize() {
    return Constants.OBJECT_ID_LENGTH;
  }

  @Override
  public boolean isTrusted() {
    return true;
  }
}
