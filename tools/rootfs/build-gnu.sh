#!/usr/bin/env bash
# Builds the gnu interpreter payload: the pinned CPython tree, the glibc runtime files and
# loader, and the kernel stack for the glibc prefix (ADR-0166). It is a second asset beside
# the musl rootfs, not part of it - an existing Space gains it by extracting over its image.
#
#   tools/rootfs/build-gnu.sh
#
# Writes build/rootfs/gnu-arm64-v8a.tzst plus a manifest of what went in. arm64 only: no
# Space runs on another ABI (ADR-0003). tools/rootfs/stage-app-artifacts.sh arm64-v8a then
# copies it into the APK's assets.
#
# Every input is pinned inside gnu/Dockerfile - the debian:trixie-slim image by digest and the
# CPython tarball by sha256, the kernel stack by requirements-gnu.lock's per-wheel hashes - so
# a rebuild reproduces the payload that produced the committed evidence. The RUN steps need
# an arm64 binfmt handler on an x86_64 host, the same one tools/rootfs/build.sh documents.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT/build/rootfs"
TARBALL="$OUT_DIR/gnu-arm64-v8a.tzst"
MANIFEST="$OUT_DIR/gnu-arm64-v8a.manifest.txt"
IMAGE="jupydroid-rootfs-gnu:arm64-v8a"
PLATFORM=linux/arm64

mkdir -p "$OUT_DIR"

echo "==> building $IMAGE for $PLATFORM"
docker build --platform "$PLATFORM" -f "$ROOT/tools/rootfs/gnu/Dockerfile" -t "$IMAGE" "$ROOT/tools/rootfs"

# The image's root IS the payload (the Dockerfile's final stage is FROM scratch), so the
# export is the overlay and nothing else - but `docker export` still carries the container's
# own files (.dockerenv, dev/, etc/resolv.conf), and an overlay that wrote those would
# clobber a Space's etc/ outside the paths it owns. The tar is filtered to the payload's
# three roots, the same set SpaceGnuImageInstaller's version bump clears.
echo "==> exporting payload"
CONTAINER="$(docker create --platform "$PLATFORM" "$IMAGE" /opt/gnu/python/bin/python3.12 --version)"
PAYLOAD_TMP="$(mktemp -d)"
trap 'docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; rm -rf "$PAYLOAD_TMP"' EXIT
docker export "$CONTAINER" | tar --wildcards -xf - -C "$PAYLOAD_TMP" \
  'opt/gnu*' 'usr/lib/aarch64-linux-gnu*' 'lib/ld-linux-aarch64.so.1'
tar -cf - -C "$PAYLOAD_TMP" opt usr lib | zstd -19 -T0 > "$TARBALL"
rm -rf "$PAYLOAD_TMP"

echo "==> recording manifest"
{
  echo "abi: arm64-v8a"
  echo "platform: $PLATFORM"
  echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "image-digest: $(docker image inspect "$IMAGE" --format '{{index .Id}}')"
  echo "tarball-sha256: $(sha256sum "$TARBALL" | cut -d' ' -f1)"
  echo "tarball-bytes: $(stat -c%s "$TARBALL")"
  echo
  echo "# the payload's own interpreter, run from inside the image - the loader, libc and"
  echo "# multiarch paths resolving is the payload's whole contract"
  docker run --rm --platform "$PLATFORM" "$IMAGE" /opt/gnu/python/bin/python3.12 -c '
import sys, platform, ipykernel, jupydroid_kernel
print("python:", sys.version.split()[0])
print("libc:", platform.libc_ver()[0])
print("ipykernel:", ipykernel.__version__)
print("kernel-module: jupydroid_kernel OK")
'
} | tee "$MANIFEST"

echo
echo "==> wrote $TARBALL ($(du -h "$TARBALL" | cut -f1))"
