# Changes

## Unreleased

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
