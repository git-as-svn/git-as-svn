#!/bin/bash -xe
cd `dirname $0`

for po in *.po; do
    lang=${po/.po/}
    xml2po -u $po src/reference/*.xml
    target=../build/docbook/$lang
    rm -fR $target
    mkdir -p `dirname $target`
    cp -R src/reference/ $target
    for xml in src/reference/*.xml; do
        xml2po -p $po $xml > $target/`basename $xml`
    done
    docxsl=/usr/share/xml/docbook/stylesheet/docbook-xsl
    fop -c src/fonts/fop.xml \
        -xml $target/index.xml \
        -xsl $docxsl/fo/docbook.xsl \
        -param fop1.extensions 1 \
        -param admon.graphics 1 \
        -param admon.graphics.extension .svg \
        -param admon.graphics.path $docxsl/images/ \
        -param highlight.source 1 \
        -pdf $target/../../distributions/manual.$lang.pdf
done

