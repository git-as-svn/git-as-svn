package svnserver.server.msg;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * Информация о подключенном клиенте.
 * <p>
 * response: ( version:number ( cap:word ... ) url:string ? ra-client:string ( ? client:string ) )
 *
 * @author a.navrotskiy
 */
public class ClientInfo {
  private final int protocolVersion;
  @NotNull
  private final String[] capabilities;
  @NotNull
  private final SVNURL url;
  @NotNull
  private final String userAgent;

  public ClientInfo(int protocolVersion, @NotNull String[] capabilities, @NotNull String url, @NotNull String userAgent) throws SVNException {
    this.protocolVersion = protocolVersion;
    this.capabilities = capabilities;
    this.url = SVNURL.parseURIEncoded(url);
    this.userAgent = userAgent;
  }

  public int getProtocolVersion() {
    return protocolVersion;
  }

  @NotNull
  public String[] getCapabilities() {
    return capabilities;
  }

  @NotNull
  public SVNURL getUrl() {
    return url;
  }

  @NotNull
  public String getUserAgent() {
    return userAgent;
  }
}
