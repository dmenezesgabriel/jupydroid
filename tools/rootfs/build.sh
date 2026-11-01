#!/usr/bin/env bash
# Builds the base Space rootfs tarball from tools/rootfs/Dockerfile.
#
#   tools/rootfs/build.sh [abi]      abi: x86_64 (default) | arm64-v8a
#
# Writes build/rootfs/rootfs-<abi>.tzst plus a manifest of what went in.
# The tarball is a build artifact, not a committed file: it is far too big for
# git and is reproducible from the pinned Dockerfile + requirements.lock (compiled from requirements.in).
#
# arm64-v8a on an x86_64 host runs every RUN under qemu, so the host needs an arm64 binfmt handler,
# and that handler does not survive a reboot. Without it the first RUN fails with
# `exec /bin/sh: exec format error`. `ls /proc/sys/fs/binfmt_misc/qemu-aarch64` says whether it is
# there, and this registers it (privileged, host-wide until the next reboot):
#
#   docker run --privileged --rm tonistiigi/binfmt --install arm64
#
# zstd rather than gzip (ADR-0012): decompressing the gzip arm64 tarball was ~9 s of
# single-threaded CPU on the tablet, the floor under every write-side optimisation.
# The decoder is zstd-jni via commons-compress, the same
# pairing Winlator's rootfs installer ships - a native lib in the APK, which the old
# comment's "save a few MB" trade-off now buys back in seconds of cold start.
set -euo pipefail

ABI="${1:-x86_64}"
case "$ABI" in
  x86_64)    PLATFORM=linux/amd64 ;;
  arm64-v8a) PLATFORM=linux/arm64 ;;
  *) echo "unknown abi '$ABI'; expected x86_64 or arm64-v8a" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT/build/rootfs"
TARBALL="$OUT_DIR/rootfs-$ABI.tzst"
MANIFEST="$OUT_DIR/rootfs-$ABI.manifest.txt"
IMAGE="jupydroid-rootfs:$ABI"

mkdir -p "$OUT_DIR"

echo "==> building $IMAGE for $PLATFORM"
docker build --platform "$PLATFORM" -t "$IMAGE" "$ROOT/tools/rootfs"

# The rootfs is a container filesystem, not an image: `docker export` flattens
# the layers, so the files deleted in the strip layer are genuinely absent.
echo "==> exporting filesystem"
CONTAINER="$(docker create --platform "$PLATFORM" "$IMAGE" /bin/true)"
trap 'docker rm -f "$CONTAINER" >/dev/null 2>&1 || true' EXIT
# -19 -T0: best ratio short of --ultra -22, multithreaded; no --long so the decoder's
# window stays the level default (~8 MB) rather than a 128 MB buffer on the tablet.
docker export "$CONTAINER" | zstd -19 -T0 > "$TARBALL"

echo "==> recording manifest"
{
  echo "abi: $ABI"
  echo "platform: $PLATFORM"
  echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "image-digest: $(docker image inspect "$IMAGE" --format '{{index .Id}}')"
  echo "tarball-sha256: $(sha256sum "$TARBALL" | cut -d' ' -f1)"
  echo "tarball-bytes: $(stat -c%s "$TARBALL")"
  echo
  echo "# interpreter + loader paths inside the root, as proot runs them"
  docker run --rm --platform "$PLATFORM" "$IMAGE" sh -c '
    echo "python3: $(readlink -f /usr/bin/python3)"
    echo "loader:  $(readlink -f /lib/ld-musl-*.so.1)"
    python3 -c "import sys; print(\"python-version: \" + sys.version.split()[0])"
    python3 -c "import jupyter_server, ipykernel; print(\"jupyter_server: \" + jupyter_server.__version__); print(\"ipykernel: \" + ipykernel.__version__)"
  '
} | tee "$MANIFEST"

echo
echo "==> wrote $TARBALL ($(du -h "$TARBALL" | cut -f1))"
