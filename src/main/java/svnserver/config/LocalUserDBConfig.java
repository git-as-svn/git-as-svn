package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.LocalUserDB;
import svnserver.auth.UserDB;

/**
 * Created by marat on 15.08.14.
 */
public final class LocalUserDBConfig implements UserDBConfig {
  @NotNull
  @Override
  public UserDB create() {
    return new LocalUserDB();
  }
}
