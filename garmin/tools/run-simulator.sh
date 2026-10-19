#!/bin/sh
# Starts the Connect IQ simulator of the active SDK in the ciq-sdk-runtime container. It shares
# the host network, so `monkeydo` (and VS Code's "Run") on the host reach it on localhost.
set -e
SDK="$(cat "$HOME/.Garmin/ConnectIQ/current-sdk.cfg" | sed 's:/*$::')"
exec docker run --rm \
  --user "$(id -u):$(id -g)" \
  --network host \
  -e HOME="$HOME" -e DISPLAY="$DISPLAY" -e XAUTHORITY=/tmp/xauth \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v "$XAUTHORITY":/tmp/xauth:ro \
  -v "$HOME/.Garmin":"$HOME/.Garmin" \
  --shm-size=512m \
  ciq-sdk-runtime "$SDK/bin/simulator"
