package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerWriter;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException {
    SvnServerWriter writer = context.getWriter();
    byte[] fileContent = "File content\n".getBytes(StandardCharsets.UTF_8);
    MessageDigest md5 = getMd5();
    md5.update(fileContent);
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(42) // rev
        .listBegin().listEnd() // props
        .listBegin();
    if (args.wantContents) {
      for (int i = 0; i < 3; ++i) {
        writer
            .listBegin()
            .string("file" + i + ".txt") // name
            .word("file") // node-kind
            .number(40) // size
            .bool(false) // has-props
            .number(42) // created-rev
            .listBegin().string("1970-01-01T00:00:00.000000Z").listEnd() // created-date
            .listBegin().listEnd() // last-author
            .listEnd();
      }
    }
    writer
        .listEnd()
        .listEnd()
        .listEnd();
  }

  private static MessageDigest getMd5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
