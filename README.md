# Overview

[![Build Status](https://travis-ci.org/bozaro/git-as-svn.svg?branch=master)](https://travis-ci.org/bozaro/git-as-svn)

Subversion frontend server for git repository (in Java).

## Python proof-of-concept implementation:

 * http://git.q42.co.uk/git_svn_server.git

## SVN protocol description

 * http://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol
 * http://svn.apache.org/repos/asf/subversion/trunk/notes/

# How to use

## Build from sources

To build from sources you need install JDK 1.8 or later and run build script.

For Linux:

    ./gradlew deployZip

For Windows:

    call gradlew.bat deployZip

When build completes you can run server executing:

    java -jar build/deploy/git-as-svn.jar --config config.example --show-config

## Run from binaries

You can download binaries archive from: https://github.com/bozaro/git-as-svn/releases

After unpacking archive you can run server executing:

    java -jar build/deploy/git-as-svn.jar --config config.example --show-config
