/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.git.GitRevision;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Get revision property list.
 * <p>
 * <pre>
 * rev-proplist
 *    params:   ( rev:number )
 *    response: ( props:proplist )
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class RevPropListCmd extends BaseCmd<RevPropListCmd.Params> {

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final SvnServerWriter writer = context.getWriter();
    final GitRevision revision = context.getBranch().getRevisionInfo(args.revision);
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .writeMap(revision.getProperties(true))
        .listEnd()
        .listEnd();
  }

  public static class Params {
    private final int revision;

    public Params(int revision) {
      this.revision = revision;
    }
  }
}
