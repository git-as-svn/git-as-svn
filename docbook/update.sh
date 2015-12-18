#!/bin/bash -xe
for po in *.po; do
    xml2po -u $po base/*.xml
    target=../build/docbook/${po/.po/}
    rm -fR $target
    mkdir -p `dirname $target`
    cp -R base/ $target
    for xml in base/*.xml; do
        xml2po -p $po $xml > $target/`basename $xml`
    done
done

