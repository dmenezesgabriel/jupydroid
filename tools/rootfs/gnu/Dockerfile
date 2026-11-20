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
# The runtime files come from the sha-pinned ubuntu-base tarball the probes measured, not from
# this image: ubuntu:24.04's digest moves, the tarball's sha256 does not. This image is only
# the environment the interpreter's own pip runs in - glibc 2.39, the same series the payload
# links. FROM scratch at the end so `docker export` yields the payload and nothing else.
#
# arm64 only: no Space runs on another ABI (ADR-0003).

FROM ubuntu:24.04@sha256:9c7f200c07e3d4a03e7249d2c16bf66a51120fb876aa2242cf31451412543289 AS gnu

ARG TARGETARCH
RUN test "$TARGETARCH" = "arm64" \
 || { echo "the gnu payload is arm64-only: no Space runs on another ABI" >&2; exit 1; }

# ADD --checksum verifies the pinned bytes at fetch; they are the same pins the glibc probes
# carry (app/src/androidTest ProbeSupport.kt). The CPython digest is the release asset's own.
ADD --checksum=sha256:04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2 \
    https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-arm64.tar.gz \
    /inputs/ubuntu-base.tar.gz
ADD --checksum=sha256:4792f81750282597fb2a3389fe18b945ed2ba6aad5e45ad406d122c58cb33081 \
    "https://github.com/astral-sh/python-build-standalone/releases/download/20261110/cpython-3.12.16%2B20261110-aarch64-unknown-linux-gnu-install_only.tar.gz" \
    /inputs/cpython.tar.gz
COPY gnu/requirements-gnu.lock jupydroid_kernel.py jupydroid_usersite.py jupydroid_usersite.pth \
     user_site_probe.py /inputs/

# The payload layout is the one the image-shapes probe measured on the tablet: the CPython
# tree under opt/gnu (its tarball's top level is `python/`), the glibc runtime files at the
# multiarch path glibc searches by default - the loader's own family, the NSS modules
# `hosts: dns` loads, and `libstdc++` the wheels' `.so`s link - and the loader copied whole
# to the PT_INTERP path. `cp -a` keeps each entry's own mode and symlink: libstdc++.so.6
# stays a link to its soname target.
RUN mkdir -p /payload/opt/gnu /payload/usr/lib/aarch64-linux-gnu /payload/lib /ubuntu-root \
 && tar -xzf /inputs/ubuntu-base.tar.gz -C /ubuntu-root \
 && tar -xzf /inputs/cpython.tar.gz -C /payload/opt/gnu \
 && for lib in ld-linux-aarch64.so.1 libc.so.6 libm.so.6 libpthread.so.0 librt.so.1 \
      libdl.so.2 libutil.so.1 libgcc_s.so.1 libstdc++.so.6 libstdc++.so.6.0.33 \
      libnss_dns.so.2 libnss_files.so.2; do \
      cp -a "/ubuntu-root/usr/lib/aarch64-linux-gnu/$lib" "/payload/usr/lib/aarch64-linux-gnu/$lib" \
   || { echo "ubuntu-base lacks $lib at usr/lib/aarch64-linux-gnu" >&2; exit 1; }; \
    done \
 && cp -L /ubuntu-root/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1 /payload/lib/ld-linux-aarch64.so.1

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
