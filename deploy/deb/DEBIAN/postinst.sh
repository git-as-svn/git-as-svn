#!/bin/sh -e
if [ -f /bin/systemctl ]; then
    /bin/systemctl daemon-reload
fi
