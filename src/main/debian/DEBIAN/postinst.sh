#!/bin/sh -e
if [ ! -f /etc/git-as-svn/git-as-svn.conf ]; then
    cp /etc/git-as-svn/git-as-svn.conf.example /etc/git-as-svn/git-as-svn.conf
fi

/usr/sbin/update-rc.d git-as-svn defaults
/usr/sbin/update-rc.d git-as-svn enable
/etc/init.d/git-as-svn restart
