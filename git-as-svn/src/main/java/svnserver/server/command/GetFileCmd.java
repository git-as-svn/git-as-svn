package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.StringHelper;
import svnserver.parser.SvnServerWriter;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Get file content.
 * <p><pre>
 * get-file
 *    params:   ( path:string [ rev:number ] want-props:bool want-contents:bool
 *    ? want-iprops:bool )
 *    response: ( [ checksum:string ] rev:number props:proplist
 *    [ inherited-props:iproplist ] )
 *    If want-contents is specified, then after sending response, server
 *    sends file contents as a series of strings, terminated by the empty
 *    string, followed by a second empty command response to indicate
 *    whether an error occurred during the sending of the file.
 *    NOTE: the standard client doesn't send want-iprops as true, it uses
 *    get-iprops, but does send want-iprops as false to workaround a server
 *    bug in 1.8.0-1.8.8.
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GetFileCmd extends BaseCmd<GetFileCmd.Params> {
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
        .listBegin().string(StringHelper.toHex(md5.digest())).listEnd() // md5
        .number(42) // rev
        .listBegin().listEnd() // props
        .listEnd()
        .listEnd();
    if (args.wantContents) {
      writer.binary(fileContent);
      writer.string("");
      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();
    }
  }

  private static MessageDigest getMd5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
