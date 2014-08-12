package svnserver.server.msg;

import org.jetbrains.annotations.NotNull;

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
  private final String url;
  @NotNull
  private final String userAgent;

  public ClientInfo(int protocolVersion, @NotNull String[] capabilities, @NotNull String url, @NotNull String userAgent) {
    this.protocolVersion = protocolVersion;
    this.capabilities = capabilities;
    this.url = url;
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
  public String getUrl() {
    return url;
  }

  @NotNull
  public String getUserAgent() {
    return userAgent;
  }
}
