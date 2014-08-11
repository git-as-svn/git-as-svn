package svnserver.server.msg;

import org.jetbrains.annotations.NotNull;

/**
 * Запрос на аутентификацию.
 * <p>
 * response: ( version:number ( cap:word ... ) url:string ? ra-client:string ( ? client:string ) )
 *
 * @author a.navrotskiy
 */
public class AuthInfoReq {
  private final int protocolVersion;
  @NotNull
  private final String[] capabilities;
  @NotNull
  private final String url;
  @NotNull
  private final String clientInfo;

  public AuthInfoReq(int protocolVersion, @NotNull String[] capabilities, @NotNull String url, @NotNull String clientInfo) {
    this.protocolVersion = protocolVersion;
    this.capabilities = capabilities;
    this.url = url;
    this.clientInfo = clientInfo;
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
  public String getClientInfo() {
    return clientInfo;
  }
}
