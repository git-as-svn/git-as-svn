package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.UserDB;

import javax.xml.bind.annotation.XmlSeeAlso;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@XmlSeeAlso({
    LDAPUserDBConfig.class,
    LocalUserDBConfig.class
})
public interface UserDBConfig {
  @NotNull
  UserDB create();
}
