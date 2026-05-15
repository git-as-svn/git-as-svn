/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jetty.io.RuntimeIOException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow
import svnserver.Loggers
import svnserver.TemporaryOutputStream
import svnserver.auth.User
import svnserver.repository.git.filter.GitFilter
import java.io.*
import java.util.*

/**
 * Delta consumer for applying svn diff on git blob.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GitDeltaConsumer internal constructor(private val writer: GitWriter, private val entry: GitEntry, file: GitFile?, private val user: User) : ISVNDeltaConsumer, Closeable {
    var properties: MutableMap<String, String>
    var originalId: GitObject<ObjectId>? = null
    private var originalMd5: String? = null
    private var oldFilter: GitFilter? = null
    private var window: SVNDeltaProcessor? = null
    private var objectId: GitObject<ObjectId>?
    private var newFilter: GitFilter?
    private val temporaryStream: TemporaryOutputStream
    private var md5: String? = null

    @Throws(SVNException::class)
    fun validateChecksum(md5: String) {
        if (window != null) {
            if (md5 != this.md5) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH))
            }
        } else if (originalMd5 != null) {
            if (originalMd5 != md5) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH))
            }
        }
    }

    @Throws(IOException::class)
    fun getObjectId(): GitObject<ObjectId>? {
        if ((originalId != null) && (originalId == objectId) && (newFilter == null)) {
            newFilter = oldFilter
            objectId = originalId
            if (oldFilter == null) {
                throw IllegalStateException("Original object ID defined, but original Filter is not defined")
            }
            migrateFilter(writer.branch.repository.getFilter(if (properties.containsKey(SVNProperty.SPECIAL)) FileMode.SYMLINK else FileMode.REGULAR_FILE, entry.rawProperties))
        }
        return objectId
    }

    @Throws(IOException::class)
    fun migrateFilter(filter: GitFilter?): Boolean {
        if (newFilter == null || objectId == null) {
            throw IllegalStateException("Original object ID defined, but original Filter is not defined")
        }
        val beforeId: GitObject<ObjectId> = objectId!!
        if (newFilter != filter) {
            val repo: Repository = writer.branch.repository.git
            TemporaryOutputStream().use { content ->
                newFilter!!.inputStream(objectId!!).use { inputStream -> filter!!.outputStream(UncloseableOutputStream(content), user).use { outputStream -> inputStream.copyTo(outputStream) } }
                content.toInputStream().use { inputStream ->
                    objectId = GitObject(repo, writer.inserter.insert(Constants.OBJ_BLOB, content.size(), inputStream))
                    newFilter = filter
                }
            }
        }
        return beforeId != objectId
    }

    @Throws(SVNException::class)
    override fun applyTextDelta(path: String?, baseChecksum: String?) {
        try {
            if ((originalMd5 != null) && (baseChecksum != null)) {
                if (baseChecksum != originalMd5) {
                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH))
                }
            }
            if (window != null) throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CMD_ERR))
            newFilter = writer.branch.repository.getFilter(if (properties.containsKey(SVNProperty.SPECIAL)) FileMode.SYMLINK else FileMode.REGULAR_FILE, entry.rawProperties)
            window = SVNDeltaProcessor()

            val base: InputStream = if ((oldFilter != null && objectId != null)) oldFilter!!.inputStream(objectId!!) else SVNFileUtil.DUMMY_IN
            val target: OutputStream = newFilter!!.outputStream(UncloseableOutputStream(temporaryStream), user)
            window!!.applyTextDelta(base, UncheckedCloseOutputStream(target), true)
        } catch (e: IOException) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e)
        }
    }

    @Throws(SVNException::class)
    override fun textDeltaChunk(path: String, diffWindow: SVNDiffWindow): OutputStream {
        if (window == null) throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CMD_ERR))
        return window!!.textDeltaChunk(diffWindow)
    }

    @Throws(SVNException::class)
    override fun textDeltaEnd(path: String?) {
        try {
            temporaryStream.use { holder ->
                if (window == null) throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CMD_ERR))
                val repo: Repository = writer.branch.repository.git
                md5 = window!!.textDeltaEnd()
                holder.toInputStream().use { stream -> objectId = GitObject(repo, writer.inserter.insert(Constants.OBJ_BLOB, holder.size(), stream)) }
                log.info("Created blob {} for file: {}", objectId!!.`object`.name, entry.fullPath)
            }
        } catch (e: IOException) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e)
        } catch (e: RuntimeIOException) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e.cause)
        }
    }

    val filterName: String
        get() {
            if (newFilter != null) return newFilter!!.name
            if (oldFilter != null) return oldFilter!!.name
            throw IllegalStateException()
        }

    @Throws(IOException::class)
    override fun close() {
        temporaryStream.close()
    }

    private class UncheckedCloseOutputStream(out: OutputStream) : FilterOutputStream(out) {
        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
        }

        override fun close() {
            try {
                super.close()
            } catch (e: IOException) {
                throw RuntimeIOException(e)
            }
        }
    }

    private class UncloseableOutputStream(out: OutputStream) : FilterOutputStream(out) {
        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
        }

        override fun close() {
            // noop
        }
    }

    companion object {
        private val log: Logger = Loggers.git
    }

    init {
        if (file != null) {
            originalMd5 = file.md5
            originalId = file.objectId
            properties = HashMap(file.properties)
            oldFilter = file.filter
        } else {
            originalMd5 = null
            originalId = null
            properties = HashMap()
            oldFilter = null
        }
        newFilter = null
        objectId = originalId
        temporaryStream = TemporaryOutputStream()
    }
}
