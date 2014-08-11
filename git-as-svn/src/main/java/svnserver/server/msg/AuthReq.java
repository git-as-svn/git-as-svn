package svnserver.server.msg;

import org.jetbrains.annotations.NotNull;

/**
 * Message from client with authentication type.
 * <p>
 * auth-response: ( mech:word [ token:string ] )
 *
 * @author a.navrotskiy
 */
public class AuthReq {
  @NotNull
  private final String mech;

  public AuthReq(@NotNull String mech) {
    this.mech = mech;
  }

  @NotNull
  public String getMech() {
    return mech;
  }
}
