#!/bin/bash -xe
cd `dirname $0`

for po in src/main/po/*.po; do
    lang=`basename ${po/.po/}`
    xml2po -u $po src/main/reference/*.xml
done

