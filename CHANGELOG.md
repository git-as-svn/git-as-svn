# Changes
## 1.1.3

 * Fix ISO 8601 date formatting.
 * Fix unexpected error message on locked file update #127.
 * Increase default token expire time to one hour (3600 sec).
 * Add string-suffix parameter for git-lfs-authenticate script.

## 1.1.2

 * Add reference to original commit as parent for prevent commit removing by `git gc` #118.
 * Fix repository mapping error #122.
 * Fix non ThreadSafe Kryo usage #121.
 * Add support for combine multiple authenticators.
 * Add support for authenticator cache.
 * Fix tree conflict on Windows after renaming file with same name in another case #123.
 * Use commit author instead of commiter identity in svn log.
 * Don't allow almost expired tokens for LFS pointer requests.

## 1.1.1

 * Fix "E210002: Network connection closed unexpectedly" on client
   update failure #114.

## 1.1.0

 * Use by default svn:eol-style = native for text files (fix #106).
 * Upload .deb package to debian repository.

## 1.0.17-alpha: Added documentation

 * Add PDF, EPUB manual.
 * Add support for anonymous authentication for public repositories.

## 1.0.16-alpha: GitLab authentication

 * Rewrite GitLab authentication #110.
 * Fix some permission check issues #110.
 * Generate token in LFS server instead pass original authentication data #105.
 * Ignore unknown GitLab hook data.

## 1.0.15-alhpa: GitLab 8.2 LFS storage layout support

 * Add support for GitLab 8.2 LFS storage layout #109.

## 1.0.14-alpha: Debian packaging

 * Add debian packaging.
 * Add configurable file logging.

## 1.0.13-alpha: Embedded git-lfs server

 * Git-lfs batch API support.
 * Add support for LDAP users without email.
 * Add support for X-Forwarded-* headers.
 * Add HTTP-requests logging.
 * Change .gitignore mapping: ignored folder now mask all content as ignored.
 * Fix git-lfs file commit.
 * Fix quote parsing for .tgitconfig file.

## 1.0.12-alpha: Initial support of GitLab integration and embedded git-lfs server

 * Initial git-lfs support (embedded git-lfs server).
 * Initial GitLab integration.
 * Import project list on startup.
 * Authentication.
 * Add support for embedded git push with hooks;
 * Git-as-svn change information moved outside git repostitory #60.
 * Configuration format changed.
 * Fixed some wildcard issues.

## 1.0.11-alpha: Bugfixes

 * Fix URL in authentication result on default port (Jenkins error: "E21005: Impossibly long
   repository root from server").
 * Fix bind on already used port with flag SO_REUSEADDR (thanks for @fcharlie, #70).
 * Add support for custom CA certificate for ldaps authentication.

## 1.0.10-alpha: Some improvements

 * Fix get file size performance issue (```svn ls```).
 * Fix update IMMEDIATES to INFINITY bug.
 * Fix NPE on absent email in LDAP.

## 1.0.9-alpha: Fixed svn update after aborted update/checkout

 * Fix svn update after aborted update/checkout.
 * Fix out-of-memory when update/checkout big directory.
 * Show version number on startup.

## 1.0.8-alpha: Add locks and multirepo support

 * Support commands: ```svn lock```/```svn unlock```.
 * Multiple repositories support.

## 1.0.7-alpha: More simple demonstration run

 * More simple demonstration run
 * ```svnsync``` support

## 1.0.6-alpha: Fixes and binary files autodetection

 * Add autodetection binary files (now file has ```svn:mime-type = application/octet-stream``` if
   it set as binary in .gitattribues or detected as binary).
 * Expose committer email to svn.
 * Fix getSize() for submodules.
 * Fix temporary file lifetime.

## 1.0.5-alpha: Persistent cache support

 * Add persistent cache support.
 * Dumb locks support.
 * Fix copy-from permission issue.

## 1.0.4-alpha

 * Improve error message when commit is rejected due to wrong properties.

## 1.0.3-alpha: Fix spaces in url

 * Fix spaces in url.
 * Add support get-locations.
 * Add mapping binary to ```svn:mime-type = svn:mime-type```

## 1.0.2-alpha

 * Fix some critical bugs.

## 1.0.1-alpha: Add support for more subversion commands

 * Fix some bugs.

## 1.0.0-alpha

 * First release.
