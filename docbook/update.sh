#!/bin/bash -xe
cd `dirname $0`

for po in *.po; do
    lang=${po/.po/}
    xml2po -u $po base/*.xml
    target=../build/docbook.$lang
    rm -fR $target
    mkdir -p `dirname $target`
    cp -R base/ $target
    for xml in base/*.xml; do
        xml2po -p $po $xml > $target/`basename $xml`
    done
    fop -c fop.xml -xml $target/manual.xml -xsl /usr/share/xml/docbook/stylesheet/docbook-xsl/fo/docbook.xsl -pdf $target/../distributions/manual.$lang.pdf
done

