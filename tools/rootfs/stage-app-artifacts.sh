#!/usr/bin/env bash
# Stages the build artifacts into the app module. Run after build.sh.
#
#   tools/rootfs/stage-app-artifacts.sh [abi]     abi: x86_64 (default) | arm64-v8a
#
# Produces two kinds of output:
#
#   app/src/main/jniLibs/<abi>/        the only files that are ever execve'd.
#       Android extracts these to /data/app/.../lib/<abi>/ with SELinux context
#       apk_data_file, where execution is allowed. A targetSdk 36 app cannot
#       execve anything under filesDir, so the two files staged here are proot
#       and its loader, the pair every Space process runs under (ADR-0073).
#       Everything else a Space runs lives inside the translated root and is
#       read, not execve'd, by the kernel.
#   app/src/main/assets/spaces/base/   the rootfs, which is only ever read and
#       mmap'd - allowed under filesDir, so it does not need this treatment.
#
# Both are generated, both are gitignored: they are reproducible from the
# digest-pinned image and far too big to commit.
set -euo pipefail

ABI="${1:-x86_64}"
case "$ABI" in
  x86_64|arm64-v8a) ;;
  *) echo "unknown abi '$ABI'; expected x86_64 or arm64-v8a" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARBALL="$ROOT/build/rootfs/rootfs-$ABI.tzst"
JNI_DIR="$ROOT/app/src/main/jniLibs/$ABI"
ASSET_DIR="$ROOT/app/src/main/assets/spaces/base"
GNU_TARBALL="$ROOT/build/rootfs/gnu-$ABI.tzst"
GNU_ASSET_DIR="$ROOT/app/src/main/assets/spaces/gnu"

if [ "$ABI" = "arm64-v8a" ]; then
  [ -f "$TARBALL" ] || { echo "missing $TARBALL; run tools/rootfs/build.sh $ABI first" >&2; exit 1; }
  [ -f "$GNU_TARBALL" ] || { echo "missing $GNU_TARBALL; run tools/rootfs/build-gnu.sh first" >&2; exit 1; }
fi

mkdir -p "$JNI_DIR" "$ASSET_DIR" "$GNU_ASSET_DIR"

# The surgery these names belonged to is gone (ADR-0073): under proot the image runs as
# itself, so no ELF is extracted, patched or staged out of the rootfs. rm rather than
# assume, so a jniLibs staged before the retirement does not keep shipping the dead files.
rm -f "$JNI_DIR"/libloader.so "$JNI_DIR"/libpython3.so "$JNI_DIR"/libbusybox.so "$JNI_DIR"/libshebang.so

# x86_64 gets nothing at all: Android's seccomp filter is defined per architecture and its
# x86_64 policy kills musl's libc startup, so no Space can run on an x86_64 device
# (ADR-0003) and SpaceAbi lists arm64 alone. The APK still installs on x86_64 emulators for
# UI work; the 33 MB rootfs would be weight nothing can run.
if [ "$ABI" = "arm64-v8a" ]; then
  echo "==> staging rootfs asset"
  cp "$TARBALL" "$ASSET_DIR/rootfs-$ABI.tzst"

  # The gnu interpreter payload ships as its own asset beside base's, so a Space that already
  # has its image gains the second interpreter by extracting over it - never by re-extracting
  # (ADR-0166).
  echo "==> staging gnu payload asset"
  cp "$GNU_TARBALL" "$GNU_ASSET_DIR/gnu-$ABI.tzst"
else
  echo "==> skipping rootfs asset for $ABI (no Space can run there)"
  rm -f "$ASSET_DIR/rootfs-$ABI.tzst"
  rm -f "$GNU_ASSET_DIR/gnu-$ABI.tzst"
fi

# proot is what every Space runs under (ADR-0073), so like the rootfs it ships on arm64
# alone. It comes from a sha-pinned Alpine package - no release branch packages it for
# aarch64 - and its loader is carved out beside it, because unset, proot execs that loader
# from filesDir. stage_proot.py carries both reasons.
if [ "$ABI" = "arm64-v8a" ]; then
  echo "==> staging proot and its loader"
  python3 "$ROOT/scripts/stage_proot.py" "$JNI_DIR"
else
  rm -f "$JNI_DIR/libproot.so" "$JNI_DIR/libproot-loader.so"
fi

echo "==> staged:"
ls -l "$JNI_DIR" "$ASSET_DIR" "$GNU_ASSET_DIR"
