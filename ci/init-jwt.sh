#!/bin/sh
set -eu
set -o pipefail

cd /secrets
key=marketplace_jwt_secret
if [ -f "$key" ]; then
    exit 0
fi

umask 077
tmp=$(mktemp .jwt.XXXXXX)
trap 'rm -f "$tmp"' EXIT
head -c 32 /dev/urandom | base64 > "$tmp"
chown 10001:10001 "$tmp"
chmod 0400 "$tmp"
ln "$tmp" "$key" || test -f "$key"
