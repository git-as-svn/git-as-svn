package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsFile;
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.Collections;

/**
 * Get file content.
 * <p><pre>
 * get-dir
 *    params:   ( path:string [ rev:number ] want-props:bool want-contents:bool
 *    ? ( field:dirent-field ... ) ? want-iprops:bool )
 *    response: ( rev:number props:proplist ( entry:dirent ... )
 *    [ inherited-props:iproplist ] )]
 *    dirent:   ( name:string kind:node-kind size:number has-props:bool
 *    created-rev:number [ created-date:string ]
 *    [ last-author:string ] )
 *    dirent-field: kind | size | has-props | created-rev | time | last-author
 *    | word
 *    NOTE: the standard client doesn't send want-iprops as true, it uses
 *    get-iprops, but does send want-iprops as false to workaround a server
 *    bug in 1.8.0-1.8.8.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GetDirCmd extends BaseCmd<GetDirCmd.Params> {
  public static class Params {
    private final String path;
    private final int[] rev;
    private final boolean wantProps;
    private final boolean wantContents;

    public Params(String path, int[] rev, boolean wantProps, boolean wantContents) {
      this.path = path;
      this.rev = rev;
      this.wantProps = wantProps;
      this.wantContents = wantContents;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    SvnServerWriter writer = context.getWriter();
    final String fullPath = context.getRepositoryPath(args.path);
    final VcsRepository repository = context.getRepository();
    final VcsRevision info = repository.getRevisionInfo(getRevision(args.rev, repository.getLatestRevision()));
    final VcsFile fileInfo = info.getFile(fullPath);
    if (fileInfo == null || (!fileInfo.isDirectory())) {
      sendError(writer, 200009, "Directory not found");
      return;
    }

    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(info.getId()) // rev
        .writeMap(args.wantProps ? fileInfo.getProperties(true) : Collections.emptyMap()) // props
        .listBegin();
    if (args.wantContents) {
      for (VcsFile item : fileInfo.getEntries()) {
        writer
            .listBegin()
            .string(item.getFileName()) // name
            .word(item.getKind().toString()) // node-kind
            .number(item.getSize()) // size
            .bool(!item.getProperties(false).isEmpty()) // has-props
            .number(item.getLastChange().getId()) // created-rev
            .listBegin().string(item.getLastChange().getDate()).listEnd() // created-date
            .listBegin().listEnd() // last-author
            .listEnd();
      }
    }
    writer
        .listEnd()
        .listEnd()
        .listEnd();
  }
}
