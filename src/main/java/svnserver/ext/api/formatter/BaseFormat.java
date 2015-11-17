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
import com.googlecode.protobuf.format.ProtobufFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.api.ProtobufFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * JSON serialization.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public abstract class BaseFormat extends ProtobufFormat {
  @NotNull
  private final ProtobufFormatter formatter;

  public BaseFormat(@NotNull ProtobufFormatter formatter, @NotNull String mimeType, @NotNull String suffix) {
    super(mimeType, suffix);
    this.formatter = formatter;
  }

  @Override
  public void write(@NotNull Message message, @NotNull OutputStream stream, @NotNull Charset charset) throws IOException {
    formatter.print(message, stream, charset);
  }

  @Nullable
  @Override
  public Message read(@NotNull Message.Builder builder, @NotNull InputStream stream, @NotNull Charset defaultCharset) throws IOException {
    formatter.merge(stream, defaultCharset, ExtensionRegistry.getEmptyRegistry(), builder);
    return builder.build();
  }
}
