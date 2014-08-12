package svnserver;

import org.jetbrains.annotations.NotNull;

/**
 * Some svn constants.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnConstants {
  @NotNull
  public static final String PROP_AUTHOR = "svn:author";
  @NotNull
  public static final String PROP_DATE = "svn:date";
  @NotNull
  public static final String PROP_LOG = "svn:log";
  @NotNull
  public static final String PROP_GIT = "git:sha1";

  public static final int ERROR_UNIMPLEMENTED = 210001;
  public static final int ERROR_NO_REVISION = 160006;
}
