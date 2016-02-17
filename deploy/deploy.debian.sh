#!/bin/bash
set -e
cd `dirname $0`/..
mkdir -p ~/.ssh
openssl aes-256-cbc -K $encrypted_1fe4e6dcebeb_key -iv $encrypted_1fe4e6dcebeb_iv -in deploy/id_rsa.enc -out build/id_rsa -d
chmod 600 build/id_rsa
scp -i build/id_rsa -B -o StrictHostKeyChecking=no build/debPackage/git-as-svn* deploy@dist.bozaro.ru:incoming/
rm build/id_rsa
