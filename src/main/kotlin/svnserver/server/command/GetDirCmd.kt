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

/**
 * Get file content.
 *
 * <pre>
 * get-dir
 * params:   ( path:string [ rev:number ] want-props:bool want-contents:bool
 * ? ( field:dirent-field ... ) ? want-iprops:bool )
 * response: ( rev:number props:proplist ( entry:dirent ... )
 * [ inherited-props:iproplist ] )]
 * dirent:   ( name:string kind:node-kind size:number has-props:bool
 * created-rev:number [ created-date:string ]
 * [ last-author:string ] )
 * dirent-field: kind | size | has-props | created-rev | time | last-author
 * | word
 * NOTE: the standard client doesn't send want-iprops as true, it uses
 * get-iprops, but does send want-iprops as false to workaround a server
 * bug in 1.8.0-1.8.8.
</pre> *
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GetDirCmd : BaseCmd<GetDirCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val fullPath: String = context.getRepositoryPath(args.path)
        val branch: GitBranch = context.branch
        val revision: GitRevision = branch.getRevisionInfo(getRevisionOrLatest(args.rev, context))
        val fileInfo: GitFile = revision.getFile(fullPath) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, fullPath + " not found in revision " + revision.id))
        if (!fileInfo.isDirectory) throw SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, fullPath + " is not a directory in revision " + revision.id))
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .number(revision.id.toLong()) // rev
            .writeMap(if (args.wantProps) fileInfo.allProperties else null) // props
            .listBegin()
            .separator()
        if (args.wantContents) {
            for (item in fileInfo.entries.values.map { it.get() }) {
                if (!context.canRead(item.fullPath)) continue
                val lastChange: GitRevision = item.lastChange
                writer
                    .listBegin()
                    .string(item.fileName) // name
                    .word(item.kind.toString()) // node-kind
                    .number(item.size) // size
                    .bool(item.properties.isNotEmpty()) // has-props
                    .number(lastChange.id.toLong()) // created-rev
                    .stringNullable(lastChange.dateString) // created-date
                    .stringNullable(lastChange.author) // last-author
                    .listEnd()
                    .separator()
            }
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

    class Params(
        val path: String,
        val rev: IntArray,
        val wantProps: Boolean,
        val wantContents: Boolean,  /*
         * This is a broken-minded SVN feature we are unlikely to support ever.
         * <p>
         * Client can declare what fields it wants to be sent for child nodes (wantContents=true).
         * <p>
         * However,
         * <ul>
         * <li>fields are not optional, so we have to fill them with junk values</li>
         * <li>They're trivial to calculate.</li>
         * <li>For additional lulz, see the email thread on dev@svn, 2012-03-28, subject
         * "buildbot failure in ASF Buildbot on svn-slik-w2k3-x64-ra",
         * &lt;http://svn.haxx.se/dev/archive-2012-03/0655.shtml&gt;.</li>
         * </ul>
         */
        val fields: Array<String>?,
        /**
         * TODO: issue #30.
         */
        val wantIProps: Boolean
    )
}
