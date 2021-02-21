/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.ObjectId
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.serializer.GroupSerializerObjectArray
import svnserver.repository.git.cache.CacheChange
import svnserver.repository.git.cache.CacheRevision
import java.io.IOException
import java.util.*

internal class CacheRevisionSerializer : GroupSerializerObjectArray<CacheRevision>() {
    @Throws(IOException::class)
    override fun serialize(out: DataOutput2, value: CacheRevision) {
        val objectId: ObjectId? = value.gitCommitId
        out.writeBoolean(objectId != null)
        if (objectId != null) ObjectIdSerializer.instance.serialize(out, objectId)
        out.writeInt(value.getRenames().size)
        for (en: Map.Entry<String, String> in value.getRenames().entries) {
            STRING.serialize(out, en.key)
            STRING.serialize(out, en.value)
        }
        out.writeInt(value.getFileChange().size)
        for (en: Map.Entry<String, CacheChange> in value.getFileChange().entries) {
            STRING.serialize(out, en.key)
            val oldFile: ObjectId? = en.value.oldFile
            out.writeBoolean(oldFile != null)
            if (oldFile != null) ObjectIdSerializer.instance.serialize(out, oldFile)
            val newFile: ObjectId? = en.value.newFile
            out.writeBoolean(newFile != null)
            if (newFile != null) ObjectIdSerializer.instance.serialize(out, newFile)
        }
    }

    @Throws(IOException::class)
    override fun deserialize(input: DataInput2, available: Int): CacheRevision {
        val objectId: ObjectId? = if (input.readBoolean()) ObjectIdSerializer.instance.deserialize(input, available) else null
        val renames: MutableMap<String, String> = TreeMap()
        val renamesCount: Int = input.readInt()
        for (i in 0 until renamesCount) {
            renames[STRING.deserialize(input, available)] = STRING.deserialize(input, available)
        }
        val fileChange: MutableMap<String, CacheChange> = TreeMap()
        val fileChangeCount: Int = input.readInt()
        for (i in 0 until fileChangeCount) {
            val name: String = STRING.deserialize(input, available)
            val oldFile: ObjectId? = if (input.readBoolean()) ObjectIdSerializer.instance.deserialize(input, available) else null
            val newFile: ObjectId? = if (input.readBoolean()) ObjectIdSerializer.instance.deserialize(input, available) else null
            fileChange[name] = CacheChange(oldFile, newFile)
        }
        return CacheRevision(objectId, renames, fileChange)
    }

    companion object {
        val instance: CacheRevisionSerializer = CacheRevisionSerializer()
    }
}
