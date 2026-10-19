#!/bin/sh
# Runs Garmin's Connect IQ SDK Manager in the ciq-sdk-runtime container (see docs/GARMIN.md),
# as your own user, with its window on your desktop (X11 / XWayland). Downloads land in
# ~/.Garmin on the host. CIQ_SDK_MANAGER_DIR = the unpacked connectiq-sdk-manager-linux folder.
set -e
MGR_DIR="${CIQ_SDK_MANAGER_DIR:-$HOME/Documents/connectiq-sdk-manager-linux}"
mkdir -p "$HOME/.Garmin"
exec docker run --rm -it \
  --user "$(id -u):$(id -g)" \
  -e HOME="$HOME" -e DISPLAY="$DISPLAY" -e XAUTHORITY=/tmp/xauth \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v "$XAUTHORITY":/tmp/xauth:ro \
  -v "$HOME/.Garmin":"$HOME/.Garmin" \
  -v "$MGR_DIR":/opt/sdkmanager:ro \
  --shm-size=512m \
  ciq-sdk-runtime /opt/sdkmanager/bin/sdkmanager
