#!/bin/bash
set -ex
cd `dirname $0`/..

function upload {
    scp -i build/id_rsa -B -o StrictHostKeyChecking=no $@ deploy@dist.bozaro.ru:incoming/
}

mkdir -p ~/.ssh
openssl aes-256-cbc -K $encrypted_1fe4e6dcebeb_key -iv $encrypted_1fe4e6dcebeb_iv -in deploy/id_rsa.enc -out build/id_rsa -d
chmod 600 build/id_rsa

upload build/debPackage/*.dsc build/debPackage/*.tar.gz build/debPackage/*.deb
upload build/debPackage/*.changes

rm build/id_rsa
