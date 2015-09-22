/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.eclipse.jetty.server.handler.ErrorHandler;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Show user-friendly exceptions with error message.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class WebExceptionMapper extends ErrorHandler implements ExceptionMapper<WebApplicationException> {
  @Context
  HttpServletRequest request;

  public Response toResponse(@NotNull WebApplicationException exception) {
    return Response
        .status(exception.getResponse().getStatus())
        .type(MediaType.TEXT_HTML_TYPE)
        .encoding(StandardCharsets.UTF_8.name())
        .entity(content(exception))
        .build();
  }

  @NotNull
  public String content(@NotNull WebApplicationException exception) {
    try {
      final StringWriter writer = new StringWriter();
      writeErrorPage(request, writer, exception.getResponse().getStatus(), exception.getMessage(), false);
      return writer.toString();
    } catch (IOException e) {
      return e.getMessage();
    }
  }
}
