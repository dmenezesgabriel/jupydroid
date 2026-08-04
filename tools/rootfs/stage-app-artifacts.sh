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
  # LOADER must match SpaceAbi.loaderLinkPath: it is baked into PT_INTERP below and
  # symlinked into nativeLibraryDir by BaseSpaceInstaller. musl keeps its loader in
  # /lib on every architecture, named after the architecture.
  x86_64)    PLATFORM=linux/amd64; LOADER=lib/ld-musl-x86_64.so.1 ;;
  arm64-v8a) PLATFORM=linux/arm64; LOADER=lib/ld-musl-aarch64.so.1 ;;
  *) echo "unknown abi '$ABI'; expected x86_64 or arm64-v8a" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="jupydroid-rootfs:$ABI"
TARBALL="$ROOT/build/rootfs/rootfs-$ABI.tgz"
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
cp "$TARBALL" "$ASSET_DIR/rootfs-$ABI.tgz"

echo "==> extracting and patching the two executable ELFs"
# patchelf runs inside the image so it edits binaries against the same libc that
# produced them, and so the host needs no patchelf installed.
docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" -v "$JNI_DIR:/out" "$IMAGE" sh -euc "
  cp \"\$(readlink -f /usr/bin/python3)\" /tmp/libpython3.so
  cp \"\$(readlink -f /$LOADER)\" /tmp/libloader.so

  # The stock PT_INTERP is /$LOADER, an absolute path that on Android resolves
  # against the device root, not the rootfs. Repoint it at the rootfs's on-device
  # location, where the installer symlinks it on into nativeLibraryDir - the only
  # place an ELF interpreter may live (evidence/step8.3/musl-runs.txt).
  patchelf --set-interpreter '$ROOTFS_ON_DEVICE/$LOADER' \
           --set-rpath '$ROOTFS_ON_DEVICE/usr/lib:$ROOTFS_ON_DEVICE/lib' \
           /tmp/libpython3.so

  cp /tmp/libpython3.so /tmp/libloader.so /out/
  chmod 0644 /out/libpython3.so /out/libloader.so
"

echo "==> staged:"
ls -l "$JNI_DIR" "$ASSET_DIR"
echo
echo "patched interpreter: $(docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" -v "$JNI_DIR:/out" "$IMAGE" \
  patchelf --print-interpreter /out/libpython3.so)"
echo "patched rpath:       $(docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" -v "$JNI_DIR:/out" "$IMAGE" \
  patchelf --print-rpath /out/libpython3.so)"
