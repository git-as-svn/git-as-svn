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
import svnserver.parser.SvnServerWriter
import svnserver.repository.git.GitBranch
import svnserver.repository.git.GitFile
import svnserver.repository.git.GitRevision
import svnserver.server.SessionContext
import java.io.IOException
import java.util.*

/**
 * Get file content.
 *
 * <pre>
 * get-iprops
 * params:   ( path:string [ rev:number ] )
 * response: ( inherited-props:iproplist )
 * New in svn 1.8.  If rev is not specified, the youngest revision is used.
</pre> *
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GetIPropsCmd : BaseCmd<GetIPropsCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val fullPath: String = context.getRepositoryPath(args.path)
        val branch: GitBranch = context.branch
        val info: GitRevision = branch.getRevisionInfo(getRevisionOrLatest(args.rev, context))
        val files = ArrayList<GitFile>()
        var index = -1
        while (true) {
            index = fullPath.indexOf('/', index + 1)
            if (index < 0) {
                break
            }
            val subPath = fullPath.substring(0, index)
            val fileInfo = info.getFile(subPath) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, subPath))
            files.add(fileInfo)
        }
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listBegin()
        for (file: GitFile in files) {
            writer
                .listBegin()
                .string(file.fullPath)
                .writeMap(file.properties)
                .listEnd()
        }
        writer
            .listEnd()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    class Params constructor(val path: String, val rev: IntArray)
}
