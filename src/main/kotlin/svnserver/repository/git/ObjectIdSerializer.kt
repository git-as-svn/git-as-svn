/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.serializer.GroupSerializerObjectArray
import java.io.IOException

internal class ObjectIdSerializer : GroupSerializerObjectArray<ObjectId>() {
    @Throws(IOException::class)
    override fun serialize(out: DataOutput2, value: ObjectId) {
        value.copyRawTo(out)
    }

    @Throws(IOException::class)
    override fun deserialize(input: DataInput2, available: Int): ObjectId {
        val raw = ByteArray(fixedSize())
        input.readFully(raw)
        return ObjectId.fromRaw(raw)
    }

    override fun fixedSize(): Int {
        return Constants.OBJECT_ID_LENGTH
    }

    override fun isTrusted(): Boolean {
        return true
    }

    companion object {
        val instance: ObjectIdSerializer = ObjectIdSerializer()
    }
}
