/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
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
import javax.naming.directory.*;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;

/**
 * Authenticates a user by binding to the directory with the DN of the entry for that user and the password
 * presented by the user. If this simple bind succeeds the user is considered to be authenticated.
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
      searchControls.setCountLimit(2);

      final NamingEnumeration<SearchResult> search = context.search("", MessageFormat.format(config.getUserSearch(), username), searchControls);
      if (!search.hasMore()) {
        log.debug("Failed to find LDAP entry for {}", username);
        return null;
      }

      final Attributes attributes = search.next().getAttributes();

      if (search.hasMore()) {
        log.error("Multiple LDAP entries found for {}", username);
        return null;
      }

      final String realName = getAttribute(attributes, config.getNameAttribute());
      final String email = getAttribute(attributes, config.getEmailAttribute());
      return new User(username, realName != null ? realName : username, email);
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

  @Nullable
  private String getAttribute(@NotNull Attributes attributes, @NotNull String name) throws NamingException {
    Attribute attribute = attributes.get(name);
    return attribute == null ? null : String.valueOf(attribute.get());
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }
}
