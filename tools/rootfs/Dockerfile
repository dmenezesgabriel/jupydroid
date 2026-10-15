# Builds the base Space rootfs: a Linux userland running stock jupyter_server.
# Not an app build - the output is a tarball that ships as an Android asset and is
# extracted to files/spaces/base/ on first run by data/space/BaseSpaceInstaller.kt.
#
# Alpine/musl, not Debian/glibc, and the reason is measured rather than stylistic:
# Android 16's app seccomp filter kills glibc's libc startup with SIGSYS, on both the
# x86_64 emulator and real arm64 hardware. musl starts cleanly under the same filter
# with everything else held constant. ADR-0003 carries the measurement.
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

# Ships on both ABIs: unlike the AT shim above, the bug this answers is real on the tablet too.
# See getaddrinfo_shim.c, and ADR-0005 for why this interposes getaddrinfo() rather than
# redirecting a path - the simpler approach was measured and ruled out.
COPY getaddrinfo_shim.c /tmp/getaddrinfo_shim.c
RUN gcc -shared -fPIC -O2 -Wall -Wextra -Werror -o /tmp/libgetaddrinfo_shim.so /tmp/getaddrinfo_shim.c \
 && strip /tmp/libgetaddrinfo_shim.so

# busybox is built here rather than taken from `apk add busybox`, and the reason is one config
# flag: Alpine ships CONFIG_FEATURE_SUID=y, which compiles in check_suid() and makes every applet
# dispatch call setgid()/setuid() - both refused to app processes by Android's seccomp filter, so
# every applet died of SIGSYS. See tools/rootfs/busybox.config and ADR-0006. Termux, which runs
# busybox in an Android app process under the same filter, sets the flag the same way we do.
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

FROM alpine:3.21@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d

# git is what jupyterlab-git's server half runs for every answer it gives (ADR-0104).
RUN apk add --no-cache python3 py3-pip git

# --break-system-packages: Alpine marks the system interpreter PEP 668
# externally-managed. This image *is* the environment, so there is nothing to
# protect it from; a venv would only add another layer of paths.
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

# The same gate for the LSP server: pylsp starts over stdio - if it cannot
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
# is proved on the device, by the instrumented tests in app/src/androidTest/.
COPY at_shim_probe.py /tmp/at_shim_probe.py
RUN if [ "$TARGETARCH" = "amd64" ]; then \
      LD_PRELOAD=/lib/libmusl_at_shim.so python3 /tmp/at_shim_probe.py; \
    fi \
 && rm -f /tmp/at_shim_probe.py

# The strip drops docs and test trees - pure bulk - but NOT bytecode caches: on the
# tablet, importing ipykernel.kernelapp costs 4.7 s cold without .pyc vs 1.3 s with
# (measured on device, ADR-0013). Shipping __pycache__ pays a few compressed MB for
# seconds off every cold server/kernel/pylsp start.
# checked-hash, not the default timestamp mode: the extractor writes files with
# extraction-time mtimes, which mismatch the mtimes timestamp pyc record - so
# every shipped cache would be invalidated and recompiled on first import anyway
# (measured: no cold-start gain with timestamp pyc). Hash pyc validate on source
# content instead, so an edited .py still recompiles as CPython intends.
#
# rfc3987-syntax is uninstalled after install, not excluded from the lock: pip
# re-resolves jsonschema's format-nongpl extra for jupyter-events either way, and
# an honest lock lists what was resolved. What we drop is the import cost -
# building its Lark parser is ~1.7 s on the tablet, inside every serverapp import
# (ADR-0014). jsonschema wraps it in suppress(ImportError), so absent is a designed
# state: the iri/iri-reference checkers simply are not registered, and nothing in
# the lock nor the shipped event schemas uses those formats (uri stays via
# rfc3986-validator). The RUN check below fails the image if that stops being true.
RUN rm -rf /usr/share/man /usr/share/doc /var/cache/apk \
 && pip3 uninstall -y --break-system-packages rfc3987-syntax \
 && find / -xdev -type d -name __pycache__ -prune -exec rm -rf {} + \
 && find /usr/lib/python3* -xdev -type d -name test -prune -exec rm -rf {} + \
 && find /usr/lib/python3* -xdev -type d -name tests -prune -exec rm -rf {} + \
 && python3 -m compileall -q --invalidation-mode checked-hash /usr/lib/python3.12

RUN python3 -c "\
import jupyter_events.logger;\
import jsonschema._format as f;\
checkers = f.draft7_format_checker.checkers;\
assert 'uri' in checkers, 'uri format checker missing';\
assert 'iri' not in checkers, 'iri checker should be absent with rfc3987-syntax gone';\
assert 'iri-reference' not in checkers, 'iri-reference checker should be absent';\
print('jupyter_events imports without rfc3987-syntax; uri checker intact')"

# The Git extension's server half is jupyterlab-git's own handlers under /git (ADR-0104), and they
# shell out to `git` (jupyterlab-git@b62a87b7 packages/core/jupyterlab_git_core/git.py). Gated
# after the strip, so a strip that took a file either needs fails here and not on the device: the
# binary runs, and the server lists the extension enabled and loadable - the wheel's own
# `etc/jupyter/jupyter_server_config.d/jupyterlab_git.json` is what enables it. The list colours
# `enabled` and `OK` with ANSI escapes even into a pipe, hence the `.*` before each word.
RUN git --version \
 && jupyter server extension list 2>&1 | tee /tmp/extensions.txt \
 && grep -Eq 'jupyterlab_git .*enabled' /tmp/extensions.txt \
 && grep -Eq 'jupyterlab_git [0-9.]+ .*OK' /tmp/extensions.txt \
 && rm /tmp/extensions.txt

# HOME must exist inside the rootfs: jupyter_server writes runtime state there,
# and on device it is not /root.
RUN mkdir -p /home/jupydroid /tmp/spaces
ENV HOME=/home/jupydroid

# Last, so no RUN above is served by a shell this line replaced: the image's own busybox becomes
# the one built without FEATURE_SUID. The 304 applet symlinks are untouched and still resolve,
# because that build uses Alpine's own config for the same version.
#
# This is the binary the device runs: under proot a Space's `/` is its own directory
# (ADR-0073), so every applet execs this file through the image's own links. If it carried
# check_suid, every command in every Space would still die of SIGSYS on the first dispatch.
COPY --from=busybox-builder /tmp/busybox/busybox /bin/busybox
