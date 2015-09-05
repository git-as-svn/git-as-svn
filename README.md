# Overview

[![Join the chat at https://gitter.im/bozaro/git-as-svn](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bozaro/git-as-svn?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://travis-ci.org/bozaro/git-as-svn.svg?branch=master)](https://travis-ci.org/bozaro/git-as-svn)

Subversion frontend server for git repository (in Java).

## Python proof-of-concept implementation:

 * http://git.q42.co.uk/git_svn_server.git

## SVN protocol description

 * http://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol
 * http://svn.apache.org/repos/asf/subversion/trunk/notes/

## GitLab integration

Now we support limited GitLab integration (see config-gitlab.example):

 * Load repository list from GitLab on startup (no dynamically update yet)
 * Authentication via GitLab API

### LFS for Git SSH users (git-lfs-authenticate)

For support SSO git-lfs authentication you need to create file ```/usr/local/bin/git-lfs-authenticate``` with content:

```
#!/bin/sh
# TOKEN - token parameter in !lfs section
# BASE  - base url
TOKEN=secret
BASE=http://localhost:8123
curl -s -d "token=${TOKEN}" -d "external=${GL_ID}" ${BASE}/$1/auth/lfs
```

Also you need some GitLab patches:

 * [#230 (gitlab-shell)](https://github.com/gitlabhq/gitlab-shell/pull/230): Add git-lfs-authenticate to server white list (merged to 7.14.1);
 * [#237 (gitlab-shell)](https://github.com/gitlabhq/gitlab-shell/pull/237): Execute git-lfs-authenticate command with original arguments;
 * [#9591 (gitlabhq)](https://github.com/gitlabhq/gitlabhq/pull/9591): Add API for lookup user information by SSH key ID (merged to 8.0.0).

### LFS for Git HTTP users

#### Password caching (client side)

You need to enable caching passwords. Otherwise, git-lfs will ask for the password for each lfs-stored file.

Turn on the credential helper so that Git will save your password in memory for some time. By default, Git will cache your password for 15 minutes.
```
$ git config --global credential.helper cache
# Set git to use the credential memory cache
```

To change the default password cache timeout, enter the following:
```
$ git config --global credential.helper 'cache --timeout=3600'
# Set the cache to timeout after 1 hour (setting is in seconds)
```

More info: https://help.github.com/articles/caching-your-github-password-in-git/

#### Reverse proxy

You need add git-as-svn to GitLab reverse proxy by modifying ```/var/opt/gitlab/nginx/conf/gitlab-http.conf``` file:

 * Add git-as-svn upstream server:
```
 upstream gitsvn {
   server localhost:8123  fail_timeout=5s;
 } 
```
 * Add resource redirection:
```
   location ~ ^.*\.git/info/lfs/ {
     proxy_read_timeout      300;
     proxy_connect_timeout   300;
     proxy_redirect          off;
 
     proxy_set_header    Host                $http_host;
     proxy_set_header    X-Real-IP           $remote_addr;
     proxy_set_header    X-Forwarded-For     $proxy_add_x_forwarded_for;
     proxy_set_header    X-Forwarded-Proto   $scheme;
     proxy_set_header    X-Frame-Options     SAMEORIGIN;
 
     proxy_pass http://gitsvn;
   }
```

Also you need to set ```baseUrl``` parameter in ```!web``` section of git-as-svn configuration file.

# How to use

## Run from binaries

For quick run you need:

 * Install Java 1.8 or later
 * Download binaries archive from: https://github.com/bozaro/git-as-svn/releases/latest
 * After unpacking archive you can run server executing:<br/>
   `java -jar git-as-svn.jar --config config.example --show-config`
 * Test connection:<br/>
   `svn ls svn://localhost/example`<br/>
   with login/password: test/test

As result:

 * Server creates bare repository with example commit in directory: `example.git`
 * The server will be available on svn://localhost/example/ url (login/password: test/test)

## Build from sources

To build from sources you need install JDK 1.8 or later and run build script.

For Linux:

    ./gradlew deployZip

For Windows:

    call gradlew.bat deployZip

When build completes you can run server executing:

    java -jar build/deploy/git-as-svn.jar --config config.example --show-config
