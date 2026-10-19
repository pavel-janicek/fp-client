# Connect IQ SDK Manager and simulator need webkit2gtk-4.0 / libsoup-2.4 / libcurl,
# which Ubuntu dropped after 22.04. This image only supplies those libraries;
# the binaries and ~/.Garmin are bind-mounted from the host.
FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      libwebkit2gtk-4.0-37 libsoup2.4-1 libjavascriptcoregtk-4.0-18 \
      libgtk-3-0 libsecret-1-0 libusb-1.0-0 libsm6 libxext6 libxrender1 \
      libcurl4 ca-certificates fonts-dejavu-core dbus-x11 xdg-utils \
    && rm -rf /var/lib/apt/lists/*
