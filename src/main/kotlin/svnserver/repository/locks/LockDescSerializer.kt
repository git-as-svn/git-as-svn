/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks

import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.serializer.GroupSerializerObjectArray
import java.io.IOException

fun DataOutput2.writeNullableUTF(value: String?) {
    if (value == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeUTF(value)
    }
}

fun DataInput2.readNullableUTF() : String? {
    return if (readBoolean()) readUTF() else null
}

class LockDescSerializer : GroupSerializerObjectArray<LockDesc>() {
    @Throws(IOException::class)
    override fun serialize(out: DataOutput2, value: LockDesc) {
        out.writeUTF(value.path)
        out.writeNullableUTF(value.branch)
        out.writeNullableUTF(value.hash)
        out.writeUTF(value.token)
        out.writeNullableUTF(value.owner)
        out.writeNullableUTF(value.comment)
        out.writeLong(value.created)
    }

    @Throws(IOException::class)
    override fun deserialize(input: DataInput2, available: Int): LockDesc {
        val path: String = input.readUTF()
        val branch: String? = input.readNullableUTF()
        val hash: String? = input.readNullableUTF()
        val token: String = input.readUTF()
        val owner: String? = input.readNullableUTF()
        val comment: String? = input.readNullableUTF()
        val created: Long = input.readLong()
        return LockDesc(path, branch, hash, token, owner, comment, created)
    }

    override fun fixedSize(): Int {
        return -1
    }

    companion object {
        val instance: LockDescSerializer = LockDescSerializer()
    }
}
