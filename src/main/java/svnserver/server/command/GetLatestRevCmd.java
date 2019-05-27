/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerWriter;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Change current path in repository.
 * <p><pre>
 * get-latest-rev
 *    params:   ( )
 *    response: ( rev:number )
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class GetLatestRevCmd extends BaseCmd<NoParams> {

  @NotNull
  @Override
  public Class<NoParams> getArguments() {
    return NoParams.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull NoParams args) throws IOException {
    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(context.getBranch().getLatestRevision().getId())
        .listEnd()
        .listEnd();
  }
}
