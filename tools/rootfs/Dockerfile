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

# The x86_64-only interposer, built in the same stage for the same reason: only the .so crosses
# over. See musl_at_shim.c for the four measured refusals it answers.
COPY musl_at_shim.c /tmp/musl_at_shim.c
RUN gcc -shared -fPIC -O2 -Wall -Wextra -Werror -o /tmp/libmusl_at_shim.so /tmp/musl_at_shim.c \
 && strip /tmp/libmusl_at_shim.so

# Ships on both ABIs: unlike the AT shim above, the bug this answers is real on the tablet too
# (evidence/p7e-0-runtime-probe/). See getaddrinfo_shim.c for why this interposes getaddrinfo()
# rather than redirecting a path, and evidence/p7e-3b-interposition-probe/ for the measurement
# that ruled the simpler approach out.
COPY getaddrinfo_shim.c /tmp/getaddrinfo_shim.c
RUN gcc -shared -fPIC -O2 -Wall -Wextra -Werror -o /tmp/libgetaddrinfo_shim.so /tmp/getaddrinfo_shim.c \
 && strip /tmp/libgetaddrinfo_shim.so

# The console-script trampoline. Static, so it needs no loader and none of stage-app-artifacts.sh's
# ELF surgery - the one shipped executable that is self-contained. See shebang_exec.c for why a
# `#!/usr/bin/python3` script cannot be run any other way under `filesDir`.
COPY shebang_exec.c /tmp/shebang_exec.c
RUN gcc -static -O2 -Wall -Wextra -Werror -o /tmp/shebang-exec /tmp/shebang_exec.c \
 && strip /tmp/shebang-exec
# Prove it dispatches, here rather than on the device: reached by a name, it must find the *other*
# file of that name on PATH (the `#!` script, not its own ELF), read the shebang, exec the
# interpreter and pass the caller's args through - and honour a `#!/usr/bin/env python3` arg. A
# trampoline that finds itself, or drops an argument, is a build error.
# `#!/bin/sh` and `#!/usr/bin/env sh`, not python - this builder stage has no interpreter but the
# dispatch logic is the interpreter-agnostic part. `JUPYDROID_SPACE_ROOT` is unset here so an
# absolute shebang keeps its path, which is why `/bin/sh` has to exist (it does, in alpine).
RUN set -eux; d=$(mktemp -d); mkdir -p "$d/bin" "$d/usr/bin"; \
    ln -s /tmp/shebang-exec "$d/bin/pip"; ln -s /tmp/shebang-exec "$d/bin/jflag"; \
    printf '#!/bin/sh\necho "D:$*"\n' > "$d/usr/bin/pip"; \
    printf '#!/usr/bin/env sh\necho "E:$*"\n' > "$d/usr/bin/jflag"; \
    test "$(PATH="$d/usr/bin:$PATH" "$d/bin/pip" --version q)" = 'D:--version q'; \
    test "$(PATH="$d/usr/bin:$PATH" "$d/bin/jflag" x)" = 'E:x'; \
    rm -rf "$d"; echo 'shebang-exec dispatches'

# busybox is built here rather than taken from `apk add busybox`, and the reason is one config
# flag: Alpine ships CONFIG_FEATURE_SUID=y, which compiles in check_suid() and makes every applet
# dispatch call setgid()/setuid() - both refused to app processes by Android's seccomp filter, so
# every applet died of SIGSYS. See tools/rootfs/busybox.config and
# evidence/p4-terminal/busybox-under-app-seccomp.md. Termux, which runs busybox in an Android app
# process under the same filter, sets the flag the same way we do.
#
# utmps is Alpine's own answer to musl having no utmpx, and it is linked **statically**, which is
# the one place this build deliberately follows Alpine's *static* recipe (APKBUILD:219,
# `-Wl,--push-state,-Bstatic`) rather than its dynamic one (APKBUILD:172). Measured the hard way:
# linked dynamically, every applet dies on device with
#   Error loading shared library libutmps.so.0.1: No such file or directory
# because the shipped rootfs carries no libutmps - the base image's busybox is Alpine's BOOTSTRAP
# build, which skips utmps entirely (APKBUILD:17). Static costs a few KB and adds no runtime
# dependency to a rootfs that has none.
#
# Built with Alpine's config, so every applet symlink the rootfs already carries still names an
# applet this binary has.
FROM alpine:3.21@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d AS busybox-builder
RUN apk add --no-cache gcc musl-dev make perl bzip2 linux-headers utmps-dev utmps-static pkgconf
COPY busybox.config /tmp/busybox.config
RUN wget -qO /tmp/busybox.tar.bz2 https://busybox.net/downloads/busybox-1.37.0.tar.bz2 \
 && echo "3311dff32e746499f4df0d5df04d7eb396382d7e108bb9250e7b519b837043a4  /tmp/busybox.tar.bz2" \
    | sha256sum -c - \
 && mkdir -p /tmp/busybox \
 && tar -xjf /tmp/busybox.tar.bz2 -C /tmp/busybox --strip-components=1 \
 && cp /tmp/busybox.config /tmp/busybox/.config \
 && make -C /tmp/busybox silentoldconfig \
 && make -C /tmp/busybox -j"$(nproc)" \
      CONFIG_EXTRA_CFLAGS="$(pkg-config --cflags --static utmps)" \
      CONFIG_EXTRA_LDFLAGS="-Wl,--push-state,-Bstatic $(pkg-config --libs --static utmps) -Wl,--pop-state"
# Fail the image build, not the device, on the two ways this binary has actually been wrong.
#
# `silentoldconfig` rewrites .config, so the flag is re-read from the file the compiler saw, and
# the fingerprint check reads the bytes rather than trusting it: "must be suid to work properly"
# is emitted inside check_suid() and nowhere else.
#
# The libutmps check is not hypothetical either - a dynamic link passed every check in this
# builder, which *has* the library, and then failed on every applet on the device, which does not.
# So the dependency is asserted here rather than the ability to run, which proves less.
RUN grep -q '^# CONFIG_FEATURE_SUID is not set$' /tmp/busybox/.config \
 && ! grep -q 'must be suid to work properly' /tmp/busybox/busybox \
 && ! grep -q 'libutmps' /tmp/busybox/busybox \
 && /tmp/busybox/busybox ls / >/dev/null \
 && /tmp/busybox/busybox | head -1

# Nothing but the ELF crosses over, so `--output type=local` writes exactly one file.
# stage-app-artifacts.sh builds this target directly; see the note there about why busybox no
# longer comes out of the rootfs tarball.
FROM scratch AS busybox-artifact
COPY --from=busybox-builder /tmp/busybox/busybox /busybox

# Same pattern for the trampoline: one file crosses over, stage-app-artifacts.sh builds this
# target directly. Static, so unlike libbusybox.so it is shipped as-is with no PT_INTERP edit.
FROM scratch AS shebang-artifact
COPY --from=sigsys-builder /tmp/shebang-exec /shebang-exec

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

# The same gate for the LSP server (P5a): pylsp starts over stdio - if it cannot
# even import under musl, the Space's language features would fail on device.
RUN pylsp --version

# Diagnostic only, and inert unless LD_PRELOAD names it, so it costs 8 KB and nothing else.
COPY --from=sigsys-builder /tmp/libsigsys_report.so /lib/libsigsys_report.so

# Ships on both ABIs; see the note beside its build above.
COPY --from=sigsys-builder /tmp/libgetaddrinfo_shim.so /lib/libgetaddrinfo_shim.so

# Prove the shim resolves a real DNS exchange and leaves the numeric-literal fallthrough intact.
COPY getaddrinfo_shim_probe.py /tmp/getaddrinfo_shim_probe.py
RUN JUPYDROID_NAMESERVERS=127.0.0.1 LD_PRELOAD=/lib/libgetaddrinfo_shim.so python3 /tmp/getaddrinfo_shim_probe.py \
 && rm -f /tmp/getaddrinfo_shim_probe.py

# **The interposer ships in the x86_64 image alone**, because on arm64 musl already issues the
# `*at` syscalls - the legacy four do not exist in that kernel ABI - so it would be dead code.
# TARGETARCH is BuildKit's, and build.sh always passes --platform, so it is always set.
ARG TARGETARCH
COPY --from=sigsys-builder /tmp/libmusl_at_shim.so /tmp/libmusl_at_shim.so
RUN if [ "$TARGETARCH" = "amd64" ]; then \
      mv /tmp/libmusl_at_shim.so /lib/libmusl_at_shim.so; \
    else \
      rm -f /tmp/libmusl_at_shim.so; \
    fi

# Prove the constructor actually installs the handler, here rather than on the device.
# getsignal() returns None precisely when something outside Python installed a handler,
# and SIG_DFL when nothing did - so this distinguishes "preloaded and armed" from
# "preloaded and silently did nothing".
RUN LD_PRELOAD=/lib/libsigsys_report.so python3 -c "\
import signal;\
handler = signal.getsignal(signal.SIGSYS);\
assert handler is None, f'SIGSYS handler not installed by the preload: {handler!r}';\
print('sigsys reporter armed')"

# Prove the interposer actually replaces the four, and that they still work when nothing is
# refusing them. Here rather than on device because a shim that fails to load, or that breaks
# an ordinary unlink, is a build error - not something to discover in a kernel that will not start.
# What this cannot show is the seccomp behaviour: a Docker build has no Android filter. That half
# is evidence/, on the device.
COPY at_shim_probe.py /tmp/at_shim_probe.py
RUN if [ "$TARGETARCH" = "amd64" ]; then \
      LD_PRELOAD=/lib/libmusl_at_shim.so python3 /tmp/at_shim_probe.py; \
    fi \
 && rm -f /tmp/at_shim_probe.py

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

# Last, so no RUN above is served by a shell this line replaced: the image's own busybox becomes
# the one built without FEATURE_SUID. The 304 applet symlinks are untouched and still resolve,
# because that build uses Alpine's own config for the same version.
#
# Strictly this is belt and braces for the *device*, where bin/busybox is deleted and re-pointed
# at nativeLibraryDir on every launch (BusyBoxApplets.relink), so the tarball's copy is never
# executed there. It is here so the image cannot quietly go back to carrying a binary that dies
# on the first applet - which is what a reader who greps the rootfs for `sh` would otherwise find.
COPY --from=busybox-builder /tmp/busybox/busybox /bin/busybox
