/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.formatter;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.ProtobufFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Binary serialization.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class FormatBinary extends ProtobufFormat {
  public FormatBinary() {
    super("application/x-protobuf", "");
  }

  @Override
  public void write(@NotNull Message message, @NotNull OutputStream stream, @NotNull Charset charset) throws IOException {
    message.writeTo(stream);
  }

  @Nullable
  @Override
  public Message read(@NotNull Message.Builder builder, @NotNull InputStream stream, @NotNull Charset charset) throws IOException {
    return builder.mergeFrom(stream).build();
  }
}
