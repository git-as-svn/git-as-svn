package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.LDAPUserDBConfig;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;

/**
 * Authenticates a user by binding to the directory with the DN of the entry for that user and the password presented by the user. If this simple bind succeeds the user is considered to be authenticated.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LDAPUserDB implements UserDB, PasswordChecker {

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LDAPUserDB.class);

  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));

  @NotNull
  private final LDAPUserDBConfig config;

  public LDAPUserDB(@NotNull LDAPUserDBConfig config) {
    this.config = config;
  }

  @Nullable
  @Override
  public User check(@NotNull String username, @NotNull String password) throws SVNException {
    final Hashtable<String, Object> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, config.getContextFactory());
    env.put(Context.PROVIDER_URL, config.getConnectionUrl());
    env.put(Context.SECURITY_AUTHENTICATION, config.getAuthentication());
    env.put(Context.SECURITY_PRINCIPAL, username);
    env.put(Context.SECURITY_CREDENTIALS, password);

    InitialDirContext context = null;
    try {
      context = new InitialDirContext(env);

      final SearchControls searchControls = new SearchControls();
      searchControls.setSearchScope(config.isUserSubtree() ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);
      searchControls.setReturningAttributes(new String[]{config.getNameAttribute(), config.getEmailAttribute()});

      final NamingEnumeration<SearchResult> search = context.search("", MessageFormat.format(config.getUserSearch(), username), searchControls);
      if (!search.hasMore())
        return null;

      final Attributes attributes = search.next().getAttributes();
      final String realName = String.valueOf(attributes.get(config.getNameAttribute()).get());
      final String email = String.valueOf(attributes.get(config.getEmailAttribute()).get());

      return new User(username, realName, email);
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
