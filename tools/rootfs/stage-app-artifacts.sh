#!/usr/bin/env bash
# Stages the Step 8.2 build artifacts into the app module. Run after build.sh.
#
#   tools/rootfs/stage-app-artifacts.sh [abi]     abi: x86_64 (default) | arm64-v8a
#
# Produces two kinds of output, and the split is the whole exec model
# (docs/PLAN.md, "Exec model"):
#
#   app/src/main/jniLibs/<abi>/        the only files that are ever execve'd.
#       Android extracts these to /data/app/.../lib/<abi>/ with SELinux context
#       apk_data_file, where execution is allowed. A targetSdk 36 app cannot
#       execve anything under filesDir.
#   app/src/main/assets/spaces/base/   the rootfs, which is only ever read and
#       mmap'd - allowed under filesDir, so it does not need this treatment.
#
# Both are generated, both are gitignored: they are reproducible from the
# digest-pinned image and far too big to commit.
set -euo pipefail

ABI="${1:-x86_64}"
case "$ABI" in
  x86_64)    PLATFORM=linux/amd64; LOADER=ld-linux-x86-64.so.2; TRIPLET=x86_64-linux-gnu ;;
  arm64-v8a) PLATFORM=linux/arm64; LOADER=ld-linux-aarch64.so.1; TRIPLET=aarch64-linux-gnu ;;
  *) echo "unknown abi '$ABI'; expected x86_64 or arm64-v8a" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="jupydroid-rootfs:$ABI"
TARBALL="$ROOT/build/rootfs/rootfs-$ABI.tar.gz"
JNI_DIR="$ROOT/app/src/main/jniLibs/$ABI"
ASSET_DIR="$ROOT/app/src/main/assets/spaces/base"

# Where the rootfs lands on device. /data/data/<pkg> is stable across installs;
# nativeLibraryDir is NOT (it carries per-install random path segments), so the
# baked-in PT_INTERP points here and the installer symlinks it across.
APP_ID="${APP_ID:-com.jupydroid.app}"
ROOTFS_ON_DEVICE="/data/data/$APP_ID/files/spaces/base"

[ -f "$TARBALL" ] || { echo "missing $TARBALL; run tools/rootfs/build.sh $ABI first" >&2; exit 1; }

mkdir -p "$JNI_DIR" "$ASSET_DIR"

echo "==> staging rootfs asset"
cp "$TARBALL" "$ASSET_DIR/rootfs-$ABI.tar.gz"

echo "==> extracting and patching the two executable ELFs"
# patchelf runs inside the image so it edits binaries against the glibc that
# produced them, and so the host needs no patchelf installed.
docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" -v "$JNI_DIR:/out" "$IMAGE" sh -euc "
  cp \"\$(readlink -f /usr/bin/python3)\" /tmp/libpython3.so
  cp \"\$(readlink -f /lib64/$LOADER 2>/dev/null || readlink -f /lib/$LOADER)\" /tmp/libld-linux.so

  # The stock PT_INTERP is /lib64/$LOADER, an absolute path that on Android
  # resolves against the device root, not the rootfs. Repoint both it and the
  # library search path at the rootfs's on-device location.
  patchelf --set-interpreter '$ROOTFS_ON_DEVICE/lib64/$LOADER' \
           --set-rpath '$ROOTFS_ON_DEVICE/usr/lib/$TRIPLET:$ROOTFS_ON_DEVICE/usr/lib' \
           /tmp/libpython3.so

  cp /tmp/libpython3.so /tmp/libld-linux.so /out/
  chmod 0644 /out/libpython3.so /out/libld-linux.so
"

echo "==> staged:"
ls -l "$JNI_DIR" "$ASSET_DIR"
echo
echo "patched interpreter: $(docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" -v "$JNI_DIR:/out" "$IMAGE" \
  patchelf --print-interpreter /out/libpython3.so)"
echo "patched rpath:       $(docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" -v "$JNI_DIR:/out" "$IMAGE" \
  patchelf --print-rpath /out/libpython3.so)"
