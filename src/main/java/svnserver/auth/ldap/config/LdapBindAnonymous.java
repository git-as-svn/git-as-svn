package svnserver.auth.ldap.config;

import com.unboundid.ldap.sdk.ANONYMOUSBindRequest;
import com.unboundid.ldap.sdk.BindRequest;
import org.jetbrains.annotations.NotNull;
import svnserver.config.serializer.ConfigType;

@ConfigType("ldapBindAnonymous")
public final class LdapBindAnonymous implements LdapBind {

  @NotNull
  public static final LdapBind instance = new LdapBindAnonymous();

  @Override
  public @NotNull BindRequest createBindRequest() {
    return new ANONYMOUSBindRequest();
  }
}
