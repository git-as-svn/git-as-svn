#!/bin/bash -xe
for po in *.po; do
    xml2po -u $po base/*.xml
done

