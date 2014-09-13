/**
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

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.Calendar;

/**
 * Change current path in repository.
 * <p><pre>
 * get-dated-rev
 *    params:   ( date:string )
 *    response: ( rev:number )
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class GetDatedRevCmd extends BaseCmd<GetDatedRevCmd.Params> {
  public final static class Params {
    @NotNull
    private String date;

    public Params(@NotNull String date) {
      this.date = date;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException {
    final SvnServerWriter writer = context.getWriter();
    final Calendar dateTime = DatatypeConverter.parseDateTime(args.date);
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(context.getRepository().getRevisionByDate(dateTime.getTime().getTime()).getId())
        .listEnd()
        .listEnd();
  }
}
