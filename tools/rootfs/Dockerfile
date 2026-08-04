# Builds the base Space rootfs: a real Debian glibc userland running stock
# jupyter_server. Not an app build - the output is a tarball that ships as an
# Android asset and is extracted to files/spaces/base/ on first run (Step 8.2).
#
# Debian rather than something hand-cross-compiled, because the whole point of
# the rootfs approach (docs/PLAN.md, "Exec model") is that Python packages stay
# ordinary manylinux wheels. bookworm gives glibc 2.36, comfortably above the
# manylinux_2_28 floor that numpy/scipy wheels target.
#
# Pinned by digest so a rebuild reproduces the image that produced the committed
# evidence. bookworm-slim as of 2026-08-04:
FROM debian:bookworm-slim@sha256:7b140f374b289a7c2befc338f42ebe6441b7ea838a042bbd5acbfca6ec875818

# patchelf is installed here rather than on the host so the ELF edits in Step 8.2
# run against the same glibc that produced the binaries.
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-pip patchelf \
 && rm -rf /var/lib/apt/lists/*

# --break-system-packages: Debian 12 marks the system interpreter PEP 668
# externally-managed. This image *is* the environment, so there is nothing to
# protect it from; a venv would only add a second layer of paths to patch.
COPY requirements.lock /tmp/requirements.lock
RUN pip3 install --no-cache-dir --break-system-packages -r /tmp/requirements.lock \
 && rm /tmp/requirements.lock

# The spike only has to answer "does this run on Android"; docs, locales and
# bytecode caches are pure bulk. Kept as one layer so the export is smaller.
RUN rm -rf \
      /usr/share/doc /usr/share/man /usr/share/info /usr/share/locale \
      /var/cache/apt /var/lib/apt/lists /var/log/* \
 && find / -xdev -type d -name __pycache__ -prune -exec rm -rf {} + \
 && find /usr/lib/python3* -xdev -type d -name test -prune -exec rm -rf {} + \
 && find /usr/lib/python3* -xdev -type d -name tests -prune -exec rm -rf {} +

# HOME must exist inside the rootfs: jupyter_server writes runtime state there,
# and on device it is not /root.
RUN mkdir -p /home/jupydroid /tmp/spaces
ENV HOME=/home/jupydroid
