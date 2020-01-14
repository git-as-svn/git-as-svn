/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.msg;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * Информация о подключенном клиенте.
 * <pre>
 *     response: ( version:number ( cap:word ... ) url:string
 *               ? ra-client:string ( ? client:string ) )
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class ClientInfo {
  private final int protocolVersion;
  @NotNull
  private final String[] capabilities;
  @NotNull
  private final SVNURL url;
  @NotNull
  private final String raClient;

  public ClientInfo(int protocolVersion, @NotNull String[] capabilities, @NotNull String url, @NotNull String raClient) throws SVNException {
    this.protocolVersion = protocolVersion;
    this.capabilities = capabilities;
    this.url = SVNURL.parseURIEncoded(url);
    this.raClient = raClient;
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
  public String getRaClient() {
    return raClient;
  }
}
