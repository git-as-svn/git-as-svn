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
import org.mapdb.Serializer;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepository;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
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
  private static final Serializer<LockDesc> serializer = new CustomSerializer();

  @NotNull
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  @NotNull
  private final SortedMap<String, LockDesc> map;
  @NotNull
  private final DB db;

  public PersistentLockFactory(@NotNull DB db) {
    this.db = db;
    this.map = db.createTreeMap("locks").valueSerializer(serializer).makeOrGet();
  }

  @NotNull
  @Override
  public <T> T wrapLockRead(@NotNull VcsRepository repo, @NotNull LockWorker<T, LockManagerRead> work) throws IOException, SVNException {
    return wrapLock(rwLock.readLock(), repo, work);
  }

  @NotNull
  @Override
  public <T> T wrapLockWrite(@NotNull VcsRepository repo, @NotNull LockWorker<T, LockManagerWrite> work) throws IOException, SVNException {
    final T result = wrapLock(rwLock.writeLock(), repo, work);
    db.commit();
    return result;
  }

  @NotNull
  private <T> T wrapLock(@NotNull Lock lock, @NotNull VcsRepository repo, @NotNull LockWorker<T, ? super TreeMapLockManager> work) throws IOException, SVNException {
    lock.lock();
    try {
      return work.exec(new TreeMapLockManager(repo, map));
    } finally {
      lock.unlock();
    }
  }

  public static class CustomSerializer implements Serializer<LockDesc>, Serializable {
    private static final byte VERSION = 1;

    @Override
    public void serialize(@NotNull DataOutput out, @NotNull LockDesc value) throws IOException {
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

    @NotNull
    @Override
    public LockDesc deserialize(@NotNull DataInput in, int available) throws IOException {
      byte version = in.readByte();
      if (version != VERSION) {
        throw new IOException("Unexpected data format");
      }
      final String path = in.readUTF();
      final String hash = in.readUTF();
      final String token = in.readUTF();
      final String owner = in.readUTF();
      final String comment = in.readBoolean() ? in.readUTF() : null;
      final long created = in.readLong();
      return new LockDesc(path, hash, token, owner, comment, created);
    }

    @Override
    public int fixedSize() {
      return -1;
    }
  }
}
