#!/bin/sh -e

# creating git group if he isn't already there
if ! getent group git >/dev/null; then
 	# Adding system group: git
	addgroup --system git >/dev/null
fi

# creating git user if he isn't already there
if ! getent passwd git >/dev/null; then
	# Adding system user: git
	adduser \
	  --system \
	  --disabled-login \
	  --ingroup git \
	  --home /home/git \
	  --shell /bin/bash \
	  --gecos git \
	  git > /dev/null
fi
