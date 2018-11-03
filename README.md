# About project
[![Join the chat at https://gitter.im/bozaro/git-as-svn](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bozaro/git-as-svn?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/bozaro/git-as-svn.svg?branch=master)](https://travis-ci.org/bozaro/git-as-svn)
[![Download](https://img.shields.io/github/release/bozaro/git-as-svn.svg)](https://github.com/bozaro/git-as-svn/releases/latest)

## Documentation links
You can read documentation by links:

 * English:
   [HTML multipage](https://bozaro.github.io/git-as-svn/html/en_US/),
   [HTML single page](https://bozaro.github.io/git-as-svn/htmlsingle/en_US/),
   [PDF](https://bozaro.github.io/git-as-svn/pdf/git-as-svn.en_US.pdf),
   [EPUB](https://bozaro.github.io/git-as-svn/epub/git-as-svn.en_US.epub)
 * Russian:
   [HTML multipage](https://bozaro.github.io/git-as-svn/html/ru_RU/),
   [HTML single page](https://bozaro.github.io/git-as-svn/htmlsingle/ru_RU/),
   [PDF](https://bozaro.github.io/git-as-svn/pdf/git-as-svn.ru_RU.pdf),
   [EPUB](https://bozaro.github.io/git-as-svn/epub/git-as-svn.ru_RU.epub)

## What is it?
This project is an implementation of the Subversion server (svn protocol) for git repository.

It allows you to work with a git repository using the console svn, TortoiseSVN, SvnKit and similar tools.

## Why do we need it?
This project was born out of division teams working on another project into two camps:

 * People who have tasted the Git and do not want to use Subversion (eg programmers); 
 * People who do not get from Git practical use and do not want to work with him, but love Subversion (eg designers).

To divide the project into two repository desire was not for various reasons.

At this point, saw the project (http://git.q42.co.uk/git_svn_server.git with Proof-of-concept implementation svn server
for git repository. After this realization svn server on top of git and didn't seem completely crazy idea (now it's
just a crazy idea) and started this project.

## Project status
Implementation status:

 * git submodules - partial
   * git submodules transparently mapped to svn
   * git submodules modification with svn not supported
 * git-lfs
 * svn properties - partial
   * some files one-way mapped to svn properties (example: .gitignore)
   * custom properties not supported
   * the commit requires that the properties of the commited file / directory exactly match the data in the repository
 * svn checkout, update, switch, diff - works
 * svn commit - works
 * svn copy, svn move - allowed copy and move commands, but copy information lost in repository
 * svn cat, ls - works
 * svn replay (svnsync) - works

## System requirements
Server-side:
 * Java 8+
 * git repository

On the client side it is strongly recommended to use the tool with support for Subversion 1.8+.

## SVN protocol description

 * http://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol
 * http://svn.apache.org/repos/asf/subversion/trunk/notes/

## GitLab integration

Now we support limited GitLab integration (see config-gitlab.example):

 * Load repository list from GitLab on startup (no dynamically update yet)
 * Authentication via GitLab API

### LFS for Git HTTP users

#### Reverse proxy

You need add git-as-svn to GitLab reverse proxy by modifying ```/var/opt/gitlab/nginx/conf/gitlab-http.conf``` file:

 * Add git-as-svn upstream server:
```
 upstream gitsvn {
   server      localhost:8123  fail_timeout=5s;
   keepalive   100;
 } 
```
 * Add resource redirection:
```
   location ~ ^.*\.git/info/lfs/ {
     proxy_read_timeout      300;
     proxy_connect_timeout   300;
     proxy_redirect          off;
 
     proxy_http_version  1.1;
     proxy_set_header    Connection          "";
 
     proxy_set_header    Host                $http_host;
     proxy_set_header    X-Real-IP           $remote_addr;
     proxy_set_header    X-Forwarded-For     $proxy_add_x_forwarded_for;
     proxy_set_header    X-Forwarded-Proto   $scheme;
     proxy_set_header    X-Frame-Options     SAMEORIGIN;

     proxy_pass http://gitsvn;
   }
```

Also you need to set ```baseUrl``` parameter in ```!web``` section of git-as-svn configuration file.

## Gitea Integration
There is also integration with Gitea >=v1.6. (Requires Sudo API) Remember to run git-as-svn as the git user.

## SVN+SSH
SSH support for Gitlab and Gitea is now available. Look at the manual.

# How to use

## Install on Ubuntu/Debian

You can install Git as Subversion by commands:
```bash
# Add bintray GPG key
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 379CE192D401AB61
# Add package source
echo "deb https://dl.bintray.com/bozaro/git-as-svn debian main" | sudo tee /etc/apt/sources.list.d/git-as-svn.list
# Install package
sudo apt-get update
sudo apt-get install git-as-svn
```

## Run from binaries

For quick run you need:

 * Install Java 1.8 or later
 * Download binaries archive from: https://github.com/bozaro/git-as-svn/releases/latest
 * After unpacking archive you can run server executing:<br/>
   `bin/git-as-svn --config doc/config.example --show-config`
 * Test connection:<br/>
   `svn ls svn://localhost/example`<br/>
   with login/password: test/test

As result:

 * Server creates bare repository with example commit in directory: `example.git`
 * The server will be available on svn://localhost/example/ url (login/password: test/test)
