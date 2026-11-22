#!/usr/bin/env bash
# Builds proot 5.4.0 patched for the seccomp SIGSYS traps as a musl-static aarch64 ELF.
#
#   tools/proot/build.sh
#
# Writes build/proot/proot.static. The upstream tarball is fetched once into
# tools/proot/ and pinned by the sha512 Alpine's own APKBUILD records for it; the
# six Alpine patches under tools/proot/patches/ are vendored and match that
# APKBUILD's sha512s, and seccomp-sigsys-enosys.patch is this project's. The
# image is pinned by digest like tools/rootfs/Dockerfile, and arm64 under qemu
# needs the binfmt handler that file's header documents:
#
#   docker run --privileged --rm tonistiigi/binfmt --install arm64
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOL_DIR="$ROOT/tools/proot"
OUT_DIR="$ROOT/build/proot"
IMAGE="jupydroid-proot:arm64-v8a"
TARBALL="$TOOL_DIR/proot-5.4.0.tar.gz"
PROOT_SHA512="c4d7b7428758c0d8f6a89ecaa01d1d7d5d9663a84b7e730e8160bc2a2385a7e278589f5dd86f5cd4f8e0736f09b3097b9efea7814096fd2d28e6032435b9ee63"

mkdir -p "$OUT_DIR"

if [ ! -f "$TARBALL" ] || ! (cd "$TOOL_DIR" && echo "$PROOT_SHA512  proot-5.4.0.tar.gz" | sha512sum -c - >/dev/null 2>&1); then
  curl -fsSL -o "$TARBALL" \
    "https://github.com/proot-me/proot/archive/v5.4.0/proot-5.4.0.tar.gz"
fi
(cd "$TOOL_DIR" && echo "$PROOT_SHA512  proot-5.4.0.tar.gz" | sha512sum -c -) | tee "$OUT_DIR/proot-tarball-sha512.txt"

echo "==> building $IMAGE for linux/arm64"
docker build --platform linux/arm64 -t "$IMAGE" "$TOOL_DIR"

CONTAINER="$(docker create --platform linux/arm64 "$IMAGE" /bin/true)"
trap 'docker rm -f "$CONTAINER" >/dev/null 2>&1 || true' EXIT
docker cp "$CONTAINER:/out/proot.static" "$OUT_DIR/proot.static"

{
  echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "image-digest: $(docker image inspect "$IMAGE" --format '{{index .Id}}')"
  echo "sha256: $(sha256sum "$OUT_DIR/proot.static" | cut -d' ' -f1)"
  echo "bytes: $(stat -c%s "$OUT_DIR/proot.static")"
  file "$OUT_DIR/proot.static" || true
} | tee "$OUT_DIR/proot.static.manifest.txt"

echo "==> wrote $OUT_DIR/proot.static"
