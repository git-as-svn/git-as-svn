#!/bin/sh -e
if [ ! -f /etc/git-as-svn/git-as-svn.conf ]; then
    cp /etc/git-as-svn/git-as-svn.conf.example /etc/git-as-svn/git-as-svn.conf
fi

if [ -f /bin/systemctl ]; then
    /bin/systemctl daemon-reload
    /bin/systemctl enable git-as-svn
    /bin/systemctl start git-as-svn
fi
