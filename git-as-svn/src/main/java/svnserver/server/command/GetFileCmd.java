package svnserver.server.command;

import org.eclipse.jgit.lib.ObjectLoader;
import org.jetbrains.annotations.NotNull;
import svnserver.StringHelper;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.Repository;
import svnserver.repository.RevisionInfo;
import svnserver.server.SessionContext;
import svnserver.server.error.ClientErrorException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, ClientErrorException {
    SvnServerWriter writer = context.getWriter();
    String fullPath = context.getRepositoryPath(args.path);
    if (fullPath.endsWith("/")) {
      sendError(writer, 200009, "Could not cat all targets because some targets are directories");
      return;
    }
    final Repository repository = context.getRepository();
    final RevisionInfo info = repository.getRevisionInfo(getRevision(args.rev, repository.getLatestRevision()));

    final byte[] buffer = new byte[64 * 1024];
    final MessageDigest md5 = getMd5();
    final ObjectLoader file = info.getFile(fullPath);
    if (file == null) {
      sendError(writer, 200009, "File not found");
      return;
    }
    try (InputStream stream = file.openStream()) {
      while (true) {
        int size = stream.read(buffer);
        if (size < 0) break;
        md5.update(buffer, 0, size);
      }
    }
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin().string(StringHelper.toHex(md5.digest())).listEnd() // md5
        .number(info.getId()) // revision id
        .listBegin().listEnd() // props
        .listEnd()
        .listEnd();
    if (args.wantContents) {
      final OutputStream stream = writer.getStream();
      stream.write(String.valueOf(file.getSize()).getBytes());
      stream.write(':');
      file.copyTo(stream);
      stream.write(' ');

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
