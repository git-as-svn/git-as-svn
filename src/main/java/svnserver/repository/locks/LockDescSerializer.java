/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

import java.io.IOException;

public final class LockDescSerializer extends GroupSerializerObjectArray<LockDesc> {

  @NotNull
  public static final LockDescSerializer instance = new LockDescSerializer();

  @Override
  public void serialize(@NotNull DataOutput2 out, @NotNull LockDesc value) throws IOException {
    out.writeUTF(value.getPath());

    if (value.getBranch() != null) {
      out.writeBoolean(true);
      out.writeUTF(value.getBranch());
    } else {
      out.writeBoolean(false);
    }

    if (value.getHash() != null) {
      out.writeBoolean(true);
      out.writeUTF(value.getHash());
    } else {
      out.writeBoolean(false);
    }

    out.writeUTF(value.getToken());
    out.writeUTF(value.getOwner());

    if (value.getComment() != null) {
      out.writeBoolean(true);
      out.writeUTF(value.getComment());
    } else {
      out.writeBoolean(false);
    }

    out.writeLong(value.getCreated());
  }

  @Override
  public LockDesc deserialize(@NotNull DataInput2 input, int available) throws IOException {
    final String path = input.readUTF();
    final String branch = input.readBoolean() ? input.readUTF() : null;
    final String hash = input.readBoolean() ? input.readUTF() : null;
    final String token = input.readUTF();
    final String owner = input.readUTF();
    final String comment = input.readBoolean() ? input.readUTF() : null;
    final long created = input.readLong();
    return new LockDesc(path, branch, hash, token, owner, comment, created);
  }

  @Override
  public int fixedSize() {
    return -1;
  }
}
