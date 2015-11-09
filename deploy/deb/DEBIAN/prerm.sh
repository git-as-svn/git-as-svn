#!/bin/sh -e
if [ -f /bin/systemctl ]; then
    /bin/systemctl stop git-as-svn
else
    /etc/init.d/git-as-svn stop
fi
