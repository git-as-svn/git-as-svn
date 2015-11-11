#!/bin/sh -e
if [ ! -f /etc/git-as-svn/git-as-svn.conf ]; then
    cp /etc/git-as-svn/git-as-svn.conf.example /etc/git-as-svn/git-as-svn.conf
fi

/usr/sbin/update-rc.d git-as-svn defaults > /dev/null
/usr/sbin/update-rc.d git-as-svn enable > /dev/null
/etc/init.d/git-as-svn restart
