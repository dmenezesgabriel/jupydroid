# Builds the base Space rootfs: a Linux userland running stock jupyter_server.
# Not an app build - the output is a tarball that ships as an Android asset and is
# extracted to files/spaces/base/ on first run (Step 8.2).
#
# Alpine/musl, not Debian/glibc, and the reason is measured rather than stylistic:
# Android 16's app seccomp filter kills glibc's libc startup with SIGSYS, on both the
# x86_64 emulator and real arm64 hardware. musl starts cleanly under the same filter
# with everything else held constant. Full evidence in evidence/step8.3/.
#
# The Jupyter stack still comes from ordinary wheels - musllinux rather than manylinux -
# so the premise of the rootfs approach is intact: no hand-cross-compiling.
#
# Pinned by digest so a rebuild reproduces the image that produced the committed
# evidence. alpine:3.21 as of 2026-08-04:

# A separate stage purely so the shipped rootfs does not grow a C toolchain: only the
# 8 KB .so crosses over. See sigsys_report.c for why it exists.
FROM alpine:3.21@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d AS sigsys-builder
RUN apk add --no-cache gcc musl-dev
COPY sigsys_report.c /tmp/sigsys_report.c
RUN gcc -shared -fPIC -O2 -Wall -Wextra -Werror -o /tmp/libsigsys_report.so /tmp/sigsys_report.c \
 && strip /tmp/libsigsys_report.so

FROM alpine:3.21@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d

# patchelf is installed here rather than on the host so the ELF edits in
# stage-app-artifacts.sh run against the same libc that produced the binaries.
RUN apk add --no-cache python3 py3-pip patchelf

# --break-system-packages: Alpine marks the system interpreter PEP 668
# externally-managed. This image *is* the environment, so there is nothing to
# protect it from; a venv would only add another layer of paths to patch.
# --only-binary=:all: is a gate, not an optimisation - if a package ever stops
# publishing a musllinux wheel we want the build to fail loudly rather than
# quietly start compiling from source under emulation.
COPY requirements.lock /tmp/requirements.lock
RUN pip3 install --no-cache-dir --break-system-packages --only-binary=:all: \
      -r /tmp/requirements.lock \
 && rm /tmp/requirements.lock

# The kernel entry point ships as a real, reviewable module rather than being
# generated at runtime from strings. Placed on the default sys.path so the
# kernelspec can name it with -m and nothing has to set PYTHONPATH.
COPY jupydroid_kernel.py /usr/lib/python3.12/site-packages/jupydroid_kernel.py

# Fail the image build, not the device, if the kernel entry point is broken: an
# ipykernel upgrade that moved init_iopub or _bind_socket must surface here.
RUN python3 -c "\
import jupydroid_kernel as k, inspect;\
from ipykernel.kernelapp import IPKernelApp;\
assert issubclass(k.JupydroidKernelApp, IPKernelApp);\
assert 'init_iopub' in vars(k.JupydroidKernelApp), 'override no longer applies';\
assert hasattr(IPKernelApp, '_bind_socket'), 'IPKernelApp._bind_socket is gone';\
assert hasattr(IPKernelApp, '_apply_curve_server_options'), 'curve options hook is gone';\
assert 'pipe' in inspect.signature(__import__('ipykernel.iostream', fromlist=['IOPubThread']).IOPubThread.__init__).parameters;\
print('kernel entry point OK')"

# Diagnostic only, and inert unless LD_PRELOAD names it, so it costs 8 KB and nothing else.
COPY --from=sigsys-builder /tmp/libsigsys_report.so /lib/libsigsys_report.so

# Prove the constructor actually installs the handler, here rather than on the device.
# getsignal() returns None precisely when something outside Python installed a handler,
# and SIG_DFL when nothing did - so this distinguishes "preloaded and armed" from
# "preloaded and silently did nothing".
RUN LD_PRELOAD=/lib/libsigsys_report.so python3 -c "\
import signal;\
handler = signal.getsignal(signal.SIGSYS);\
assert handler is None, f'SIGSYS handler not installed by the preload: {handler!r}';\
print('sigsys reporter armed')"

# The spike only has to answer whether this runs on Android; docs and bytecode
# caches are pure bulk. One layer, so the export is smaller.
RUN rm -rf /usr/share/man /usr/share/doc /var/cache/apk \
 && find / -xdev -type d -name __pycache__ -prune -exec rm -rf {} + \
 && find /usr/lib/python3* -xdev -type d -name test -prune -exec rm -rf {} + \
 && find /usr/lib/python3* -xdev -type d -name tests -prune -exec rm -rf {} +

# HOME must exist inside the rootfs: jupyter_server writes runtime state there,
# and on device it is not /root.
RUN mkdir -p /home/jupydroid /tmp/spaces
ENV HOME=/home/jupydroid
