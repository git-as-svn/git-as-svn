#!/bin/bash
pip install ghp-import --user $USER &&
$HOME/.local/bin/ghp-import -n build/doc &&
git push -qf https://${GH_TOKEN}@github.com/${TRAVIS_REPO_SLUG}.git gh-pages
