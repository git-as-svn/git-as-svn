/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Protobuf formatter.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface ProtobufFormat {
  @NotNull
  String getMimeType();

  @NotNull
  String getSuffix();

  void write(@NotNull Message message, @NotNull HttpServletResponse output) throws IOException;

  @Nullable
  Message read(@NotNull Message.Builder builder, @NotNull HttpServletRequest input) throws IOException;
}
