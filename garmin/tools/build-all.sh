#!/bin/sh
# Builds a side-loadable .prg for every product in manifest.xml, named with the version from
# source/Version.mc: bin/fitpub-<device>_<x_y_z>.prg (dots → underscores). Signing key:
# $CIQ_DEVELOPER_KEY, default ~/garmin/developer_key.der (see docs/GARMIN.md).
set -e
cd "$(dirname "$0")/.."
SDK="$(sed 's:/*$::' "$HOME/.Garmin/ConnectIQ/current-sdk.cfg")/bin"
KEY="${CIQ_DEVELOPER_KEY:-$HOME/garmin/developer_key.der}"
VERSION="$(sed -n 's/.*const NAME = "\([0-9.]*\)".*/\1/p' source/Version.mc | tr . _)"
[ -n "$VERSION" ] || { echo "No version in source/Version.mc" >&2; exit 1; }
mkdir -p bin
for DEVICE in $(sed -n 's/.*<iq:product id="\([^"]*\)".*/\1/p' manifest.xml); do
    OUT="bin/fitpub-${DEVICE}_${VERSION}.prg"
    echo "== $OUT"
    "$SDK/monkeyc" -f monkey.jungle -d "$DEVICE" -o "$OUT" -y "$KEY" -w
done
