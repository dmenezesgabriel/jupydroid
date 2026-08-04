#!/usr/bin/env bash
# Builds the base Space rootfs tarball (Step 8.1) from tools/rootfs/Dockerfile.
#
#   tools/rootfs/build.sh [abi]      abi: x86_64 (default) | arm64-v8a
#
# Writes build/rootfs/rootfs-<abi>.tgz plus a manifest of what went in.
# The tarball is a build artifact, not a committed file: it is far too big for
# git and is reproducible from the pinned Dockerfile + requirements.lock.
#
# .tgz, not .tar.gz: aapt special-cases the .gz extension and gunzips the asset at
# build time, which would put a 182 MB raw tar in the APK. gzip rather than zstd
# because the installer that unpacks
# this on device (Step 8.2b) gets GZIPInputStream from the JDK, whereas zstd
# would mean shipping a native decompressor to save a few MB of APK.
set -euo pipefail

ABI="${1:-x86_64}"
case "$ABI" in
  x86_64)    PLATFORM=linux/amd64 ;;
  arm64-v8a) PLATFORM=linux/arm64 ;;
  *) echo "unknown abi '$ABI'; expected x86_64 or arm64-v8a" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT/build/rootfs"
TARBALL="$OUT_DIR/rootfs-$ABI.tgz"
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
docker export "$CONTAINER" | gzip -9 > "$TARBALL"

echo "==> recording manifest"
{
  echo "abi: $ABI"
  echo "platform: $PLATFORM"
  echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "image-digest: $(docker image inspect "$IMAGE" --format '{{index .Id}}')"
  echo "tarball-sha256: $(sha256sum "$TARBALL" | cut -d' ' -f1)"
  echo "tarball-bytes: $(stat -c%s "$TARBALL")"
  echo
  echo "# python + interpreter paths the app needs (Step 8.2 patches these)"
  docker run --rm --platform "$PLATFORM" "$IMAGE" sh -c '
    echo "python3: $(readlink -f /usr/bin/python3)"
    echo "loader:  $(readlink -f /lib64/ld-linux-x86-64.so.2 2>/dev/null ||
                     readlink -f /lib/ld-linux-aarch64.so.1 2>/dev/null)"
    python3 -c "import sys; print(\"python-version: \" + sys.version.split()[0])"
    python3 -c "import jupyter_server, ipykernel; print(\"jupyter_server: \" + jupyter_server.__version__); print(\"ipykernel: \" + ipykernel.__version__)"
  '
} | tee "$MANIFEST"

echo
echo "==> wrote $TARBALL ($(du -h "$TARBALL" | cut -f1))"
