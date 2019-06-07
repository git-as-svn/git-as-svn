/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class Loggers {
  @NotNull
  public static final Logger git = LoggerFactory.getLogger("git");
  @NotNull
  public static final Logger gitea = LoggerFactory.getLogger("gitea");
  @NotNull
  public static final Logger gitlab = LoggerFactory.getLogger("gitlab");
  @NotNull
  public static final Logger ldap = LoggerFactory.getLogger("ldap");
  @NotNull
  public static final Logger lfs = LoggerFactory.getLogger("lfs");
  @NotNull
  public static final Logger misc = LoggerFactory.getLogger("misc");
  @NotNull
  public static final Logger svn = LoggerFactory.getLogger("svn");
  @NotNull
  public static final Logger web = LoggerFactory.getLogger("web");
}
