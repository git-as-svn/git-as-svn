/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.formatter;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import org.jetbrains.annotations.NotNull;
import ru.bozaro.protobuf.ProtobufFormat;

import java.io.*;
import java.nio.charset.Charset;

/**
 * Text serialization.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class FormatText extends ProtobufFormat {
  public FormatText() {
    super("text/plain", ".txt");
  }

  @Override
  public void write(@NotNull Message message, @NotNull OutputStream stream, @NotNull Charset charset) throws IOException {
    try (OutputStreamWriter writer = new OutputStreamWriter(stream, charset)) {
      TextFormat.print(message, writer);
    }
  }

  @NotNull
  @Override
  public Message.Builder read(@NotNull Message.Builder builder, @NotNull InputStream stream, @NotNull Charset charset) throws IOException {
    TextFormat.merge(new InputStreamReader(stream, charset), ExtensionRegistry.getEmptyRegistry(), builder);
    return builder;
  }
}
