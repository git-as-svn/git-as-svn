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

  /**
   * This is a URL whose format is defined by the JNDI provider.
   * It is usually an LDAP URL that specifies the domain name of the directory server to connect to,
   * and optionally the port number and distinguished name (DN) of the required root naming context.
   * <p>
   * Example:
   */
  @NotNull
  private String connectionUrl = "ldap://localhost:389/ou=groups,dc=mycompany,dc=com";
  /**
   * The JNDI context factory used to acquire our InitialContext. By
   * default, assumes use of an LDAP server using the standard JNDI LDAP
   * provider.
   */
  @NotNull
  private final String contextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
  /**
   * The type of authentication to use.
   */
  @NotNull
  private String authentication = "DIGEST-MD5";
  /**
   * The search scope. Set to <code>true</code> if you wish to search the entire subtree rooted at the <code>userBase</code> entry. The default value of <code>false</code> requests a single-level search including only the top level.
   */
  private boolean userSubtree;
  /**
   * Pattern specifying the LDAP search filter to use after substitution of the username.
   */
  @NotNull
  private String userSearch = "(mail={0})";
  /**
   * LDAP attribute, containing user name.
   */
  @NotNull
  private String nameAttribute = "name";
  /**
   * LDAP attribute, containing user email.
   */
  @NotNull
  private String emailAttribute = "mail";

  public LDAPUserDB(@NotNull String connectionUrl, @NotNull String userSearch, boolean userSubtree) {
    this.userSearch = userSearch;
    this.userSubtree = userSubtree;
    this.connectionUrl = connectionUrl;
  }

  @Nullable
  @Override
  public User check(@NotNull String username, @NotNull String password) throws SVNException {
    final Hashtable<String, Object> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
    env.put(Context.PROVIDER_URL, connectionUrl);
    env.put(Context.SECURITY_AUTHENTICATION, authentication);
    env.put(Context.SECURITY_PRINCIPAL, username);
    env.put(Context.SECURITY_CREDENTIALS, password);

    InitialDirContext context = null;
    try {
      context = new InitialDirContext(env);

      final SearchControls searchControls = new SearchControls();
      searchControls.setSearchScope(userSubtree ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);

      final NamingEnumeration<SearchResult> search = context.search("", MessageFormat.format(userSearch, username), searchControls);
      if (!search.hasMore())
        return null;

      final Attributes attributes = search.next().getAttributes();
      final String realName = String.valueOf(attributes.get(nameAttribute).get());
      final String email = String.valueOf(attributes.get(emailAttribute).get());

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
