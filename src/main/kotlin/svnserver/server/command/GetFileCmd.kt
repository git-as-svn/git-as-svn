/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import svnserver.StreamHelper
import svnserver.parser.SvnServerWriter
import svnserver.repository.git.GitBranch
import svnserver.repository.git.GitFile
import svnserver.repository.git.GitRevision
import svnserver.server.SessionContext
import java.io.IOException

/**
 * Get file content.
 *
 * <pre>
 * get-file
 * params:   ( path:string [ rev:number ] want-props:bool want-contents:bool
 * ? want-iprops:bool )
 * response: ( [ checksum:string ] rev:number props:proplist
 * [ inherited-props:iproplist ] )
 * If want-contents is specified, then after sending response, server
 * sends file contents as a series of strings, terminated by the empty
 * string, followed by a second empty command response to indicate
 * whether an error occurred during the sending of the file.
 * NOTE: the standard client doesn't send want-iprops as true, it uses
 * get-iprops, but does send want-iprops as false to workaround a server
 * bug in 1.8.0-1.8.8.
</pre> *
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GetFileCmd : BaseCmd<GetFileCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val fullPath: String = context.getRepositoryPath(args.path)
        if (fullPath.endsWith("/")) throw SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Could not cat all targets because some targets are directories"))
        val branch: GitBranch = context.branch
        val revision: GitRevision = branch.getRevisionInfo(getRevisionOrLatest(args.rev, context))
        val fileInfo: GitFile = revision.getFile(fullPath) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, fullPath + " not found in revision " + revision.id))
        if (fileInfo.isDirectory) throw SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, fullPath + " is a directory in revision " + revision.id))
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listBegin().string(fileInfo.md5).listEnd() // md5
            .number(revision.id.toLong()) // revision id
            .writeMap(if (args.wantProps) fileInfo.allProperties else null)
            .listEnd()
            .listEnd()
        if (args.wantContents) {
            val buffer = ByteArray(WINDOW_SIZE)
            fileInfo.openStream().use { stream ->
                while (true) {
                    val read: Int = StreamHelper.readFully(stream, buffer, 0, buffer.size)
                    writer.binary(buffer, 0, read)
                    if (read == 0) {
                        break
                    }
                }
            }
            writer
                .listBegin()
                .word("success")
                .listBegin()
                .listEnd()
                .listEnd()
        }
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    class Params(
        val path: String, val rev: IntArray, val wantProps: Boolean, val wantContents: Boolean,
        /**
         * TODO: issue #30.
         */
        private val wantIProps: Boolean
    )

    companion object {
        private const val WINDOW_SIZE: Int = 1024 * 100
    }
}
