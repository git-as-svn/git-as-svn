#!/bin/bash
mkdir -p ~/.ssh
openssl aes-256-cbc -K $encrypted_1fe4e6dcebeb_key -iv $encrypted_1fe4e6dcebeb_iv -in deploy/id_rsa.enc -out ~/.ssh/id_rsa -d
dput -c dput.cf -u deploy build/debPackage/*.changes
