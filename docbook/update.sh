#!/bin/bash -xe
cd `dirname $0`

../gradlew docbookSinglePo

for po in src/main/po/*.po; do
    lang=`basename ${po/.po/}`
    xml2po -u $po $lang/build/main/index.l10n.xml
done

