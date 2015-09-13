/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api.formatter;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.api.ProtobufFormat;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Binary serialization.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class FormatBinary implements ProtobufFormat {
  @NotNull
  @Override
  public String getMimeType() {
    return "application/x-protobuf";
  }

  @NotNull
  @Override
  public String getSuffix() {
    return "";
  }

  @Override
  public void write(@NotNull Message message, @NotNull HttpServletResponse output) throws IOException {
    message.writeTo(output.getOutputStream());
  }

  @Nullable
  @Override
  public Message read(@NotNull Message.Builder builder, @NotNull HttpServletRequest input) throws IOException {
    return builder.mergeFrom(input.getInputStream()).build();
  }
}
