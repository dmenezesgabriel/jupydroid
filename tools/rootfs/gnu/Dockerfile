# syntax=docker/dockerfile:1
#
# Builds the gnu interpreter payload a Space gains beside its musl rootfs (ADR-0166): the
# pinned python-build-standalone CPython at opt/gnu/python, the glibc runtime files it links
# at usr/lib/aarch64-linux-gnu with its loader at lib/ld-linux-aarch64.so.1 - where the
# interpreter's PT_INTERP looks - and the kernel stack pip-installed into the CPython's own
# prefix. It ships as its own asset, not inside the rootfs tarball, so an existing Space gains
# it by extracting over its image and never re-extracts (ADR-0073's rule, applied to a second
# tree).
#
# The runtime files come from the digest-pinned debian:trixie-slim image itself - glibc 2.41
# and the GCC-14 runtime, Debian stable, lighter than ubuntu:24.04's glibc 2.39 at the same
# multiarch paths. This image is also the environment the interpreter's own pip runs in.
# FROM scratch at the end so `docker export` yields the payload and nothing else.
#
# arm64 only: no Space runs on another ABI (ADR-0003).

FROM debian:trixie-slim@sha256:a72b4433de1cf7d6bae0c4cee74d0547f58601a643b3375f070c51600d63b6b2 AS gnu

ARG TARGETARCH
RUN test "$TARGETARCH" = "arm64" \
 || { echo "the gnu payload is arm64-only: no Space runs on another ABI" >&2; exit 1; }

# ADD --checksum verifies the pinned bytes at fetch; it is the same pin the glibc probes
# carry for their own CPython (app/src/androidTest ProbeSupport.kt).
# The CPython digest is the release asset's own.
ADD --checksum=sha256:4792f81750282597fb2a3389fe18b945ed2ba6aad5e45ad406d122c58cb33081 \
    "https://github.com/astral-sh/python-build-standalone/releases/download/20261110/cpython-3.12.16%2B20261110-aarch64-unknown-linux-gnu-install_only.tar.gz" \
    /inputs/cpython.tar.gz

# Runtime libraries the published wheels link that trixie-slim does not ship, pinned the
# same way the CPython tarball is: Debian's pool path is stable per version and the sha256
# is the archive's own, so a rebuild reproduces it. libgomp1 is the OpenMP half of the
# GCC-14 runtime LightGBM's `.so` links; the rest are the X11/GL set opencv-python's
# bundled Qt5 plugins need plus glib and zlib - measured off the wheel's own DT_NEEDED
# entries and the closure they pull, not guessed.
ADD --checksum=sha256:43e84cb7971d8a4d8870cab6204e998ea4f23ee1339bb0ff556dd881bac82d25 \
    "https://deb.debian.org/debian/pool/main/g/gcc-14/libgomp1_14.2.0-19_arm64.deb" \
    /inputs/libgomp1.deb
ADD --checksum=sha256:7ccff90234484bf21ae5ecb79d25b6a84b2f4038f65dd139085b2e3a937cd8dc \
    "https://deb.debian.org/debian/pool/main/g/gcc-14/libatomic1_14.2.0-19_arm64.deb" \
    /inputs/libatomic1.deb
ADD --checksum=sha256:d84a783b818f2386627604e64b793eb4ac5bb9ea1ba321a194a1c9c82fe09a01 \
    "https://deb.debian.org/debian/pool/main/libf/libffi/libffi8_3.4.8-2_arm64.deb" \
    /inputs/libffi8.deb
ADD --checksum=sha256:d446f0ea85fb9dc411219ce861e59e5eabe1981c796918771a7d2aa97cc72481 \
    "https://deb.debian.org/debian/pool/main/g/glib2.0/libglib2.0-0t64_2.84.4-3~deb13u5_arm64.deb" \
    /inputs/libglib2.0-0t64.deb
ADD --checksum=sha256:0e41b3d7ad8ad7fcd36dd4d61ca91a70018478fc4af652e044f4be770784ddff \
    "https://deb.debian.org/debian/pool/main/z/zlib/zlib1g_1.3.dfsg+really1.3.1-1+deb13u1_arm64.deb" \
    /inputs/zlib1g.deb
ADD --checksum=sha256:d0178198e80ed4cacdececabe2c112ec88c7a9258cc11a55b8e267ab14a90d82 \
    "https://deb.debian.org/debian/pool/main/libx/libxcb/libxcb1_1.17.0-2+b1_arm64.deb" \
    /inputs/libxcb1.deb
ADD --checksum=sha256:646f2d3f2165c8eebceb7f4aaca31e97a818bb11a045964833a9f764b738bac2 \
    "https://deb.debian.org/debian/pool/main/libx/libx11/libx11-6_1.8.12-1_arm64.deb" \
    /inputs/libx11-6.deb
ADD --checksum=sha256:4906619bbec923da6808cb7e4e817a7667d13cd43bc7d98c77702a03bb103f42 \
    "https://deb.debian.org/debian/pool/main/libs/libsm/libsm6_1.2.6-1_arm64.deb" \
    /inputs/libsm6.deb
ADD --checksum=sha256:048874f2296660a5e925844635f4d050cb054e7d3459cb15b4c30afb113fbecb \
    "https://deb.debian.org/debian/pool/main/libi/libice/libice6_1.1.1-1_arm64.deb" \
    /inputs/libice6.deb
ADD --checksum=sha256:29ec7b8cf1c9a47055de643eb0a1921854e762b4789a7f3bc3b0dea498fd4da9 \
    "https://deb.debian.org/debian/pool/main/libg/libglvnd/libglvnd0_1.7.0-1+b2_arm64.deb" \
    /inputs/libglvnd0.deb
ADD --checksum=sha256:7943f99962aa3316515760bf44e66f0c15a3c813ad750b784b43e319fa2e6003 \
    "https://deb.debian.org/debian/pool/main/libg/libglvnd/libgl1_1.7.0-1+b2_arm64.deb" \
    /inputs/libgl1.deb
ADD --checksum=sha256:0245aafaac5e68ca9511f23c5bebf010327e63c65790c03dbb80bf14d485956d \
    "https://deb.debian.org/debian/pool/main/libg/libglvnd/libglx0_1.7.0-1+b2_arm64.deb" \
    /inputs/libglx0.deb
ADD --checksum=sha256:ac1061728670f4626adaa1288953a0e6fb801c9cae72ee1c3231e63e2609d23a \
    "https://deb.debian.org/debian/pool/main/libx/libxau/libxau6_1.0.11-1_arm64.deb" \
    /inputs/libxau6.deb
ADD --checksum=sha256:e10bbb0802181992ecf091e9425171850eee16068729c109214ba4924f81fb52 \
    "https://deb.debian.org/debian/pool/main/libx/libxdmcp/libxdmcp6_1.1.5-1_arm64.deb" \
    /inputs/libxdmcp6.deb
ADD --checksum=sha256:27cf208c6d2924b22ed3b9ceff304b992e1f07ef7b0ef00239585595581dac99 \
    "https://deb.debian.org/debian/pool/main/libx/libxext/libxext6_1.3.4-1+b3_arm64.deb" \
    /inputs/libxext6.deb
COPY gnu/requirements-gnu.lock jupydroid_kernel.py jupydroid_usersite.py jupydroid_usersite.pth \
     user_site_probe.py /inputs/

# The payload layout is the one the image-shapes probe measured on the tablet: the CPython
# tree under opt/gnu (its tarball's top level is `python/`), the glibc runtime files at the
# multiarch path glibc searches by default - the loader's own family, the NSS modules
# `hosts: dns` loads, and the GCC-14 runtime `libstdc++`, `libgcc_s` and `libgomp` the
# wheels' `.so`s link - and the loader copied whole to the PT_INTERP path. `cp -a` keeps
# each entry's own mode and symlink: libstdc++.so.6 stays a link to its soname target,
# which the glob copies beside it; the pinned debs unpack the same way, each contributing
# its `.so` files and their soname links to the multiarch directory.
RUN mkdir -p /payload/opt/gnu /payload/usr/lib/aarch64-linux-gnu /payload/lib \
 && tar -xzf /inputs/cpython.tar.gz -C /payload/opt/gnu \
 && for lib in ld-linux-aarch64.so.1 libc.so.6 libm.so.6 libpthread.so.0 librt.so.1 \
      libdl.so.2 libutil.so.1 libgcc_s.so.1 libnss_dns.so.2 libnss_files.so.2; do \
      cp -a "/usr/lib/aarch64-linux-gnu/$lib" "/payload/usr/lib/aarch64-linux-gnu/$lib" \
   || { echo "debian lacks $lib at usr/lib/aarch64-linux-gnu" >&2; exit 1; }; \
    done \
 && cp -a /usr/lib/aarch64-linux-gnu/libstdc++.so.6* /payload/usr/lib/aarch64-linux-gnu/ \
 && cp -a /usr/lib/aarch64-linux-gnu/libpcre2-8.so.0* \
      /usr/lib/aarch64-linux-gnu/libmount.so.1* \
      /usr/lib/aarch64-linux-gnu/libblkid.so.1* \
      /usr/lib/aarch64-linux-gnu/libselinux.so.1* \
      /payload/usr/lib/aarch64-linux-gnu/ \
 && for deb in libgomp1 libatomic1 libffi8 libglib2.0-0t64 zlib1g libxcb1 libx11-6 libsm6 \
      libice6 libglvnd0 libgl1 libglx0 libxau6 libxdmcp6 libxext6; do \
      dpkg-deb -x "/inputs/$deb.deb" "/tmp/$deb" \
   && cp -a "/tmp/$deb/usr/lib/aarch64-linux-gnu/"*.so* /payload/usr/lib/aarch64-linux-gnu/ \
   || { echo "the $deb deb yields no aarch64 libs" >&2; exit 1; }; \
    done \
 && cp -L /usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1 /payload/lib/ld-linux-aarch64.so.1

# The kernel stack, installed by the interpreter's own pip into its own prefix - the same
# pip a Space's `%pip install` runs, so the lock's manylinux wheels are the ones the device
# resolves. --require-hashes fails the build on a substituted artifact rather than shipping it.
RUN /payload/opt/gnu/python/bin/python3.12 -m pip install --no-cache-dir --require-hashes \
      -r /inputs/requirements-gnu.lock

# The kernel entry point and the user-site hook ship as modules beside the packages, where
# the musl image puts them for /usr/bin/python3 (tools/rootfs/Dockerfile). The hook is the
# same two files: a glibc interpreter's user base needs it on sys.path before the first
# install writes it, exactly as ADR-0164 measured for musl.
RUN cp /inputs/jupydroid_kernel.py /inputs/jupydroid_usersite.py /inputs/jupydroid_usersite.pth \
      /payload/opt/gnu/python/lib/python3.12/site-packages/

# Fail the image build, not the device: the interpreter must start on the payload's own
# glibc files - this RUN is the first proof the loader, libc and the multiarch path resolve -
# and the kernel entry point must hold against the ipykernel the lock pinned, the same gate
# the musl image runs.
RUN /payload/opt/gnu/python/bin/python3.12 -c "\
import sys, platform, jupydroid_kernel as k, inspect;\
from ipykernel.kernelapp import IPKernelApp;\
assert issubclass(k.JupydroidKernelApp, IPKernelApp);\
assert 'init_iopub' in vars(k.JupydroidKernelApp), 'override no longer applies';\
assert hasattr(IPKernelApp, '_bind_socket'), 'IPKernelApp._bind_socket is gone';\
print('gnu interpreter OK:', sys.version.split()[0], platform.libc_ver()[0])"

# The same user-site gate the musl image runs, against the payload's interpreter.
RUN PYTHONUSERBASE=/tmp/probe-pkgs /payload/opt/gnu/python/bin/python3.12 /inputs/user_site_probe.py \
 && rm -rf /tmp/probe-pkgs

# Bytecode caches ship checked-hash, as the musl image's do (ADR-0013): extraction-time
# mtimes would invalidate timestamp pyc, and a cold kernel start pays the recompile. Test
# trees go first so they are neither shipped nor compiled.
RUN find /payload/opt/gnu/python/lib/python3.12 -type d -name __pycache__ -prune -exec rm -rf {} + \
 && find /payload/opt/gnu/python/lib/python3.12 -type d \( -name test -o -name tests \) -prune -exec rm -rf {} + \
 && /payload/opt/gnu/python/bin/python3.12 -m compileall -q --invalidation-mode checked-hash \
      /payload/opt/gnu/python/lib/python3.12

FROM scratch
COPY --from=gnu /payload /
