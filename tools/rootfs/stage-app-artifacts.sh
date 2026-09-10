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
  # INTERPRETER must match BaseSpaceInstaller.INTERPRETER_PATH for the same reason: that is the
  # rootfs path the installer replaces with a link into nativeLibraryDir (P7e-1), and the link is
  # only right because the file it replaces is the one staged here as libpython3.so.
  # PLATFORM is the same mapping build.sh uses; busybox is compiled here rather than read out of
  # the tarball, so this script now needs to know what to build it for.
  x86_64)    LOADER=lib/ld-musl-x86_64.so.1;  INTERPRETER=usr/bin/python3.12; PLATFORM=linux/amd64 ;;
  arm64-v8a) LOADER=lib/ld-musl-aarch64.so.1; INTERPRETER=usr/bin/python3.12; PLATFORM=linux/arm64 ;;
  *) echo "unknown abi '$ABI'; expected x86_64 or arm64-v8a" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_DIR="$ROOT/tools/rootfs"
# patchelf runs on the HOST architecture, whatever ABI is being staged. It rewrites ELF headers
# and does not execute the binary, so an x86_64 patchelf edits an aarch64 ELF correctly - measured,
# and the loader it produces is byte-identical (sha256) to one staged the old way. This is what
# makes the script run on a machine with no arm64 binfmt registered, which is otherwise a silent
# prerequisite: `docker run --platform linux/arm64` fails with `exec format error`.
PATCHELF_IMAGE="${PATCHELF_IMAGE:-jupydroid-rootfs:x86_64}"
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

# x86_64 gets the ELFs but not the rootfs. Android's seccomp filter is defined per
# architecture and its x86_64 policy kills musl's libc startup, so no Space can run on
# an x86_64 device (evidence/step8.3/musl-is-arm64-only.txt) and SpaceAbi lists arm64
# alone. The native libs still ship so the APK installs on x86_64 emulators for UI work;
# the 33 MB rootfs would be 33 MB that nothing can ever execute.
if [ "$ABI" = "arm64-v8a" ]; then
  echo "==> staging rootfs asset"
  cp "$TARBALL" "$ASSET_DIR/rootfs-$ABI.tgz"
else
  echo "==> skipping rootfs asset for $ABI (no Space can run there)"
  rm -f "$ASSET_DIR/rootfs-$ABI.tgz"
fi

echo "==> extracting the loader and the interpreter from the tarball"
# From the tarball rather than from the image, because the tarball IS `docker export` of that
# image - so the bytes are the same - and reading it needs no emulation.
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"' EXIT
tar -xzf "$TARBALL" -C "$STAGE_DIR" "$LOADER" "$INTERPRETER"
cp "$STAGE_DIR/$LOADER"      "$STAGE_DIR/libloader.so"
cp "$STAGE_DIR/$INTERPRETER" "$STAGE_DIR/libpython3.so"

echo "==> building busybox (P4b-2: the one without FEATURE_SUID)"
# busybox is the exception to "from the tarball", and the reason is P4b-2: Alpine's stock build
# sets CONFIG_FEATURE_SUID=y, so every applet dispatch calls setgid()/setuid() and Android's app
# seccomp filter kills it with SIGSYS before it prints a byte. The Dockerfile builds one without
# that flag; this takes it from that stage directly, so the ELF staged here is the fixed one even
# when build/rootfs/*.tgz predates the fix.
#
# It is the applets, not this file, that make the shell work: the image ships 304 symlinks
# pointing absolutely at /bin/busybox, and neither they nor the ELF is reachable on device.
# BaseSpaceInstaller points bin/busybox at this library and makes the applets relative to it, and
# busybox reads its applet from the basename of the path it was reached by - which is why the
# applets are not pointed here directly (evidence/p4-terminal/busybox-dispatch.md).
docker buildx build --platform "$PLATFORM" --target busybox-artifact \
  --output "type=local,dest=$STAGE_DIR/busybox-out" "$DOCKER_DIR"
cp "$STAGE_DIR/busybox-out/busybox" "$STAGE_DIR/libbusybox.so"

echo "==> repointing PT_INTERP and RPATH"
# The stock PT_INTERP is /$LOADER, an absolute path that on Android resolves against the device
# root, not the rootfs. Repoint it at the rootfs's on-device location, where the installer symlinks
# it on into nativeLibraryDir - the only place an ELF interpreter may live
# (evidence/step8.3/musl-runs.txt). The loader itself is not patched: it *is* the interpreter.
docker run --rm --platform linux/amd64 --user "$(id -u):$(id -g)" -v "$STAGE_DIR:/out" "$PATCHELF_IMAGE" sh -euc "
  for elf in /out/libpython3.so /out/libbusybox.so; do
    patchelf --set-interpreter '$ROOTFS_ON_DEVICE/$LOADER' \
             --set-rpath '$ROOTFS_ON_DEVICE/usr/lib:$ROOTFS_ON_DEVICE/lib' \
             \"\$elf\"
  done
"

install -m 0644 "$STAGE_DIR/libloader.so" "$STAGE_DIR/libpython3.so" "$STAGE_DIR/libbusybox.so" "$JNI_DIR/"

echo "==> staged:"
ls -l "$JNI_DIR" "$ASSET_DIR"
echo
for elf in libpython3.so libbusybox.so; do
  echo "$elf interpreter: $(docker run --rm --platform linux/amd64 --user "$(id -u):$(id -g)" \
    -v "$JNI_DIR:/out" "$PATCHELF_IMAGE" patchelf --print-interpreter "/out/$elf")"
  echo "$elf rpath:       $(docker run --rm --platform linux/amd64 --user "$(id -u):$(id -g)" \
    -v "$JNI_DIR:/out" "$PATCHELF_IMAGE" patchelf --print-rpath "/out/$elf")"
done
