package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LDAPUserDB implements UserDB, PasswordChecker {

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LDAPUserDB.class);

  @NotNull
  private final String providerURL;

  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));

  public LDAPUserDB(@NotNull String providerURL) {
    this.providerURL = providerURL;
  }

  @Nullable
  @Override
  public User check(@NotNull String username, @NotNull String password) throws SVNException {

    final SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

    final Hashtable<String, Object> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, providerURL);
    env.put(Context.SECURITY_AUTHENTICATION, "DIGEST-MD5");
    env.put(Context.SECURITY_PRINCIPAL, username);
    env.put(Context.SECURITY_CREDENTIALS, password);

    InitialDirContext context = null;
    try {
      context = new InitialDirContext(env);
      // TODO: extract username/email from ldap
      return new User(username, "Artem V. Navrotskiy", "bozaro@users.noreply.github.com");
    } catch (AuthenticationException e) {
      return null;
    } catch (NamingException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, e.getMessage()), e);
    } finally {
      if (context != null)
        try {
          context.close();
        } catch (NamingException e) {
          log.error(e.getMessage(), e);
        }
    }
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }
}
