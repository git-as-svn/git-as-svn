/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api.formatter;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.googlecode.protobuf.format.AbstractCharBasedFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.api.ProtobufFormat;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JSON serialization.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public abstract class BaseFormat extends ProtobufFormat {
  @NotNull
  private final AbstractCharBasedFormatter format;

  public BaseFormat(@NotNull AbstractCharBasedFormatter format, @NotNull String mimeType, @NotNull String suffix) {
    super(mimeType, suffix);
    this.format = format;
  }

  @Override
  public void write(@NotNull Message message, @NotNull HttpServletResponse output) throws IOException {
    format.print(message, output.getWriter());
  }

  @Override
  @Nullable
  public Message read(@NotNull Message.Builder builder, @NotNull HttpServletRequest input) throws IOException {
    format.merge(input.getReader(), ExtensionRegistry.getEmptyRegistry(), builder);
    return builder.build();
  }
}
