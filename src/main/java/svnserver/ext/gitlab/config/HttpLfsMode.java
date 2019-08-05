/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;

@ConfigType("httpLfs")
public final class HttpLfsMode implements LfsMode {
  @NotNull
  static final LfsMode instance = new HttpLfsMode();

  @Nullable
  @Override
  public LfsReaderFactory readerFactory(@NotNull LocalContext context) {
    return null;
  }
}
