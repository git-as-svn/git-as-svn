#!/bin/sh -e
if [ -f /bin/systemctl ]; then
    /bin/systemctl stop git-as-svn
fi
