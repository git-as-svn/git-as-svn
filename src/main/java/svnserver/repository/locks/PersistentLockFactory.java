/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import org.mapdb.serializer.GroupSerializerObjectArray;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.LocalContext;
import svnserver.repository.git.GitRepository;

import java.io.IOException;
import java.util.SortedMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persistent lock manager.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class PersistentLockFactory implements LockManagerFactory {
  @NotNull
  private static final GroupSerializerObjectArray<LockDesc> serializer = new CustomSerializer();

  @NotNull
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  @NotNull
  private final SortedMap<String, LockDesc> map;
  @NotNull
  private final DB db;

  public PersistentLockFactory(@NotNull LocalContext context) {
    this.db = context.getShared().getCacheDB();
    this.map = db.treeMap("locks:" + context.getName(), Serializer.STRING, serializer).createOrOpen();
  }

  @NotNull
  @Override
  public <T> T wrapLockRead(@NotNull GitRepository repo, @NotNull LockWorker<T, LockManagerRead> work) throws IOException, SVNException {
    return wrapLock(rwLock.readLock(), repo, work);
  }

  @NotNull
  @Override
  public <T> T wrapLockWrite(@NotNull GitRepository repo, @NotNull LockWorker<T, LockManagerWrite> work) throws IOException, SVNException {
    final T result = wrapLock(rwLock.writeLock(), repo, work);
    db.commit();
    return result;
  }

  @NotNull
  private <T> T wrapLock(@NotNull Lock lock, @NotNull GitRepository repo, @NotNull LockWorker<T, ? super TreeMapLockManager> work) throws IOException, SVNException {
    lock.lock();
    try {
      return work.exec(new TreeMapLockManager(repo, map));
    } finally {
      lock.unlock();
    }
  }

  public static class CustomSerializer extends GroupSerializerObjectArray<LockDesc> {
    private static final byte VERSION = 1;

    @Override
    public void serialize(@NotNull DataOutput2 out, @NotNull LockDesc value) throws IOException {
      out.writeByte(VERSION);
      out.writeUTF(value.getPath());
      out.writeUTF(value.getHash());
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
      byte version = input.readByte();
      if (version != VERSION) {
        throw new IOException("Unexpected data format");
      }
      final String path = input.readUTF();
      final String hash = input.readUTF();
      final String token = input.readUTF();
      final String owner = input.readUTF();
      final String comment = input.readBoolean() ? input.readUTF() : null;
      final long created = input.readLong();
      return new LockDesc(path, hash, token, owner, comment, created);
    }

    @Override
    public int fixedSize() {
      return -1;
    }
  }
}
