# Builds proot 5.4.0 as a musl-static aarch64 ELF, patched so a seccomp SIGSYS stop is
# answered by writing -ENOSYS into the tracee's result register - what a guest SIGSYS
# handler returning normally does. It covers the optional syscalls a glibc loader issues
# before any preload constructor can arm a handler (`set_robust_list`, then `rseq`) and
# every refusal under PROOT_SIGSYS_ENOSYS; dropping the signal alone leaves the kernel's
# rolled-back register (on arm64, the call's first argument), not -ENOSYS. Without it a
# glibc loader dies before that handler exists.
#
# The recipe is Alpine's own (aports community/proot APKBUILD at
# a9e2a2dc46f377eb25884f7fea9834492de7f518): the six patches under patches/ are
# theirs verbatim - their sha512s are the APKBUILD's - and
# seccomp-sigsys-enosys.patch is this project's, applied last. GPL-2.0-or-later:
# the complete corresponding source is the pinned upstream tarball plus these
# patches, both fetched or vendored by tools/proot/build.sh, and this repository
# is public.
#
# Only the static build is made: a Space execs it from nativeLibraryDir, where a
# dynamically linked proot could not find its own libraries. Pinned by digest like
# tools/rootfs/Dockerfile; arm64 under qemu needs the binfmt handler that file's
# header documents.
FROM alpine:3.23@sha256:469c9ea410b9215eaee7976ba6c0639039b444be8e5d638f7abbada189971eb2

# proot-static's makedepends at the pinned aports commit. The tarball is COPYed in
# already verified by build.sh, so nothing here needs the network but apk.
RUN apk add --no-cache build-base \
    bsd-compat-headers libarchive-dev linux-headers py3-docutils \
    talloc-dev talloc-static uthash-dev

COPY proot-5.4.0.tar.gz /tmp/proot-5.4.0.tar.gz
COPY patches/ /tmp/patches/
RUN tar xzf /tmp/proot-5.4.0.tar.gz -C /tmp \
 && cd /tmp/proot-5.4.0 \
 && for p in fix-basename tests-musl-compat clone3 loongarch64 riscv64 gcc15 seccomp-sigsys-enosys; do \
      patch -p1 -s < "/tmp/patches/$p.patch" || exit 1; \
    done \
 && cp -r src src-static \
 && make -C src-static proot CC=gcc V=1 VERSION=5.4.0 LDFLAGS="-static -ltalloc"

# The artifact contract, checked in-image: a static (no INTERP) AArch64 ELF.
RUN mkdir -p /out \
 && cp /tmp/proot-5.4.0/src-static/proot /out/proot.static \
 && test -x /out/proot.static \
 && ! readelf -lW /out/proot.static | grep -q INTERP \
 && readelf -h /out/proot.static | grep -q 'Machine:.*AArch64'
