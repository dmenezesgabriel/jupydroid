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
# evidence. alpine:3.23 (3.23.8) as of 2026-11-16, 3.21 having reached end of life. `apk add` never
# upgrades what the base already holds, so the digest is what moves the base's own packages: this one
# carries libcrypto3/libssl3 3.5.9-r0 and musl 1.2.5-r24.
#
# It does not carry the fixed zlib. Alpine publishes its base images less often than its package
# repository: on 2026-11-16 every tag (3.22.8, 3.23.8, 3.24.4, edge) held zlib 1.3.2-r1 or older while
# v3.23/main had 1.4-r0, built 2026-11-06, the release that fixes CVE-2026-4738. The shipped stage
# therefore installs zlib at that exact version (see below), and the advisory scan reads the image
# this file builds, because a scan of the bare base cannot pass while the base lags.

# A separate stage purely so the shipped rootfs does not grow a C toolchain: only the
# 8 KB .so crosses over. See sigsys_report.c for why it exists.
FROM alpine:3.24@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf AS sigsys-builder
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

# Ships on both ABIs for the same reason: SELinux's link() denial is platform policy, not an
# arch difference. See proc_fd_shim.c for the apk-tools publish path it reroutes.
COPY proc_fd_shim.c /tmp/proc_fd_shim.c
RUN gcc -shared -fPIC -O2 -Wall -Wextra -Werror -o /tmp/libproc_fd_shim.so /tmp/proc_fd_shim.c \
 && strip /tmp/libproc_fd_shim.so

# Its build-time probe is a real binary, not a script, because the claim under test is that the
# access() interposition fires - only a caller reaching access() through the PLT can show that.
COPY proc_fd_probe.c /tmp/proc_fd_probe.c
RUN gcc -O2 -Wall -Wextra -Werror -o /tmp/proc_fd_probe /tmp/proc_fd_probe.c

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
FROM alpine:3.24@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf AS busybox-builder
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

FROM alpine:3.24@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf

# git is what jupyterlab-git's server half runs for every answer it gives (ADR-0104).
#
# zlib is pinned to one exact version, not upgraded with a bare `apk upgrade`: the build must fail on
# the day the repository drops 1.4-r0 (Alpine keeps only a package's newest version) instead of
# shipping whatever it holds then. When that happens, move the pin to the new version after reading
# the advisories it answers; once a base image carries a fixed zlib, delete the pin.
RUN apk add --no-cache python3 py3-pip git zlib=1.4-r0

# --break-system-packages: Alpine marks the system interpreter PEP 668
# externally-managed. This image *is* the environment, so there is nothing to
# protect it from; a venv would only add another layer of paths.
# --only-binary=:all: is a gate, not an optimisation - if a package ever stops
# publishing a musllinux wheel we want the build to fail loudly rather than
# quietly start compiling from source under emulation.
#
# --require-hashes: the lock is compiled from requirements.in with the sha256 of every wheel, so a
# substituted or re-uploaded artifact fails this build instead of shipping (ADR-0123's rule, for pip).
# The hashes cover the musllinux wheels of both ABIs, so one lock serves the arm64 and x86_64 builds.
#
# pip and setuptools are pinned in the lock too, so this install replaces the copies apk put there:
# Alpine 3.23's py3-pip 25.1.1-r1 and the py3-setuptools 80.9.0-r2 it depends on are the repository's
# newest, and the lock holds later ones from PyPI. apk's database still names the old versions.
COPY requirements.lock /tmp/requirements.lock
RUN pip3 install --no-cache-dir --break-system-packages --only-binary=:all: --require-hashes \
      -r /tmp/requirements.lock \
 && rm /tmp/requirements.lock

# Its own hashed lock (jupyter-ai-tools.lock, compiled with `uv pip compile --no-deps`), because
# --require-hashes also covers a package installed alone.
# --no-deps because jupyter-ai-tools' one dependency the lock leaves out is jupyterlab-ai-commands,
# whose Python module only declares a labextension (jupyterlab-ai-commands@2743d32
# `jupyterlab_ai_commands/__init__.py`). That frontend is the half the app plays. Its
# jupyterlab-diff chain would also bring all of jupyterlab 4.6.4. The dependencies the tools import
# (jupyterlab-commands-toolkit, pycrdt, jupyter_ydoc, jupyter_server) are pinned in the lock.
COPY jupyter-ai-tools.lock /tmp/jupyter-ai-tools.lock
RUN pip3 install --no-cache-dir --break-system-packages --only-binary=:all: --require-hashes --no-deps \
      -r /tmp/jupyter-ai-tools.lock \
 && rm /tmp/jupyter-ai-tools.lock

# Before the strip, so compileall caches the edited source. See acp_lazy_import_patch.py for why the
# ACP client's permission route is edited, and acp_lazy_import_probe.py below for the gate on it.
COPY acp_lazy_import_patch.py /tmp/acp_lazy_import_patch.py
RUN python3 /tmp/acp_lazy_import_patch.py \
 && rm -f /tmp/acp_lazy_import_patch.py

# The same cut for the MCP server: see mcp_lazy_import_patch.py, and mcp_lazy_import_probe.py below.
COPY mcp_lazy_import_patch.py /tmp/mcp_lazy_import_patch.py
RUN python3 /tmp/mcp_lazy_import_patch.py \
 && rm -f /tmp/mcp_lazy_import_patch.py

# The bearer at the HTTP layer, one step earlier than mcp_middleware's: the transport mints a
# session before its first message dispatches, so refusing inside message dispatch still left a
# tokenless caller holding a session and its streams. See mcp_http_bearer_patch.py; the refusal it
# installs is what mcp_bearer_probe.py proves below.
COPY mcp_http_bearer_patch.py /tmp/mcp_http_bearer_patch.py
RUN python3 /tmp/mcp_http_bearer_patch.py \
 && rm -f /tmp/mcp_http_bearer_patch.py

# Same reason to come before the strip. See acp_replayed_updates_patch.py for why the ACP client drops a
# loaded session's replayed history by its place in the stream, and acp_replayed_history_probe.py below.
COPY acp_replayed_updates_patch.py acp_replayed_updates.py /tmp/
RUN python3 /tmp/acp_replayed_updates_patch.py \
 && rm -f /tmp/acp_replayed_updates_patch.py /tmp/acp_replayed_updates.py

# The kernel entry point ships as a real, reviewable module rather than being
# generated at runtime from strings. Placed on the default sys.path so the
# kernelspec can name it with -m and nothing has to set PYTHONPATH.
COPY jupydroid_kernel.py /usr/lib/python3.12/site-packages/jupydroid_kernel.py

# The MCP server offers the agent only the tools the app answers (insert_cell), with none
# discovered from entry points - jupyterlab-commands-toolkit's own would hand it every JupyterLab
# command - and only to a caller holding the server's token (jupydroid_mcp_bearer.py).
#
# The server's own config file, not an entry in `jupyter_server_config.d/`. jupyter_server 2.20.0
# reads that directory only for `jpserver_extensions`. Placed there, this config was ignored, and
# mcp_bearer_probe.py found both of the toolkit's tools open to a caller with no token.
COPY jupydroid_mcp_bearer.py /usr/lib/python3.12/site-packages/jupydroid_mcp_bearer.py
COPY jupyter_server_config.json /usr/etc/jupyter/jupyter_server_config.json

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

# **Every interpreter in a Space adds its user base, before the first install has written it.**
# `site.addusersitepackages` adds the directory only when it already exists, and a Space's `pkgs/`
# starts empty, so the kernel that runs `%pip install` never has the path its own install writes
# into: the cell below cannot import what it installed until the kernel restarts. The `.pth` is the
# hook (the shape `distutils-precedence.pth` beside it already uses), and it names no interpreter
# version: ADR-0164 records the measurement and the four other shapes weighed.
COPY jupydroid_usersite.py /usr/lib/python3.12/site-packages/jupydroid_usersite.py
COPY jupydroid_usersite.pth /usr/lib/python3.12/site-packages/jupydroid_usersite.pth

# Fail the image build, not the device: one interpreter whose user base does not exist yet, the
# shape of a kernel started before the Space's first install. See user_site_probe.py.
COPY user_site_probe.py /tmp/user_site_probe.py
RUN PYTHONUSERBASE=/tmp/probe-pkgs python3 /tmp/user_site_probe.py \
 && rm -rf /tmp/probe-pkgs /tmp/user_site_probe.py

# The other half of the same decision: a venv keeps its isolation. `site` never processes the
# image's site-packages in one, so the hook does not load, and `ENABLE_USER_SITE` is false there
# too - which is what makes a venv's own `%pip` the only thing its kernels see (ADR-0164).
RUN PYTHONUSERBASE=/tmp/probe-pkgs python3 -m venv --without-pip /tmp/probe-venv \
 && PYTHONUSERBASE=/tmp/probe-pkgs /tmp/probe-venv/bin/python -c "\
import site, sys;\
assert not site.ENABLE_USER_SITE, f'a venv enabled the user site: {site.ENABLE_USER_SITE!r}';\
assert site.getusersitepackages() not in sys.path, 'a venv put the user site on sys.path';\
print('venv keeps its isolation')" \
 && rm -rf /tmp/probe-venv

# Diagnostic only, and inert unless LD_PRELOAD names it, so it costs 8 KB and nothing else.
COPY --from=sigsys-builder /tmp/libsigsys_report.so /lib/libsigsys_report.so

# Ships on both ABIs; see the note beside its build above.
COPY --from=sigsys-builder /tmp/libgetaddrinfo_shim.so /lib/libgetaddrinfo_shim.so

# Prove the shim resolves a real DNS exchange and leaves the numeric-literal fallthrough intact.
COPY getaddrinfo_shim_probe.py /tmp/getaddrinfo_shim_probe.py
RUN JUPYDROID_NAMESERVERS=127.0.0.1 LD_PRELOAD=/lib/libgetaddrinfo_shim.so python3 /tmp/getaddrinfo_shim_probe.py \
 && rm -f /tmp/getaddrinfo_shim_probe.py

# Ships on both ABIs; see the note beside its build above.
COPY --from=sigsys-builder /tmp/libproc_fd_shim.so /lib/libproc_fd_shim.so

# Prove the interposer answers ENOENT for the directory apk-tools probes and leaves every other
# access() - /proc/self/fd/<n> included - working. Here rather than on device because a shim
# that fails to load is a build error; whether apk then completes an install is the device
# tests' question (ApkInstallDeviceTest).
COPY --from=sigsys-builder /tmp/proc_fd_probe /tmp/proc_fd_probe
RUN LD_PRELOAD=/lib/libproc_fd_shim.so /tmp/proc_fd_probe \
 && rm -f /tmp/proc_fd_probe

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
#
# nbdime's own server extension is switched off, while nbdime stays installed. jupyterlab-git uses
# nbdime only as a library (`from nbdime import diff_notebooks, merge_notebooks`,
# jupyterlab-git@b62a87b7 packages/core/jupyterlab_git_core/git.py), and nothing calls nbdime's
# handlers: the app has no WebView for its pages. Loaded anyway through the wheel's own
# `jupyter_server_config.d/nbdime.json`, its imports cost 587 ms of every server start on the
# tablet (`python3 -X importtime`). `disable --sys-prefix` rewrites that same file to `nbdime: false`,
# so the image carries one switch for it and not two that disagree. The positive `disabled` check
# also fails if nbdime were missing, which a negative one would not.
RUN git --version \
 && jupyter server extension disable --sys-prefix nbdime \
 && jupyter server extension list 2>&1 | tee /tmp/extensions.txt \
 && grep -Eq 'jupyterlab_git .*enabled' /tmp/extensions.txt \
 && grep -Eq 'jupyterlab_git [0-9.]+ .*OK' /tmp/extensions.txt \
 && grep -Eq 'nbdime .*disabled' /tmp/extensions.txt \
 && rm /tmp/extensions.txt

# The AI extension's server half is jupyter-ai's ACP chain: jupyterlab-chat serves a chat over a
# JSON WebSocket at `/api/chat/ws/<path>` whenever no RTC provider is installed, and none is here;
# jupyter-ai's router hands each new message to the persona its metadata names; the persona manager
# loads the personas; and the ACP client's personas spawn an agent CLI the user installs in the
# Space (jupyterlab_chat 0.25.2 `__init__.py`, jupyter_ai_acp_client 0.3.3 `acp_personas/`). Each
# wheel enables itself through its own `etc/jupyter/jupyter_server_config.d/<module>.json`, so the
# gate is that all four are listed enabled and loadable, after the strip as above.
RUN jupyter server extension list 2>&1 | tee /tmp/extensions.txt \
 && for module in jupyterlab_chat jupyter_ai_router jupyter_ai_persona_manager jupyter_ai_acp_client; do \
      grep -Eq "$module .*enabled" /tmp/extensions.txt \
   && grep -Eq "$module [0-9.]+ .*OK" /tmp/extensions.txt \
   || { echo "server extension $module is not enabled and OK" >&2; exit 1; }; \
    done \
 && rm /tmp/extensions.txt

# The ACP client's import stays off every server start's clock. See acp_lazy_import_probe.py.
COPY acp_lazy_import_probe.py /tmp/acp_lazy_import_probe.py
RUN python3 /tmp/acp_lazy_import_probe.py \
 && rm -f /tmp/acp_lazy_import_probe.py

# A chat loaded again writes none of the history its agent replays. See acp_replayed_history_probe.py.
COPY acp_replayed_history_probe.py /tmp/acp_replayed_history_probe.py
RUN python3 /tmp/acp_replayed_history_probe.py \
 && rm -f /tmp/acp_replayed_history_probe.py

# The agent's notebook tools are jupyter-ai's server half: jupyter-server-mcp serves them over MCP,
# and with no RTC provider here each one asks a web client - the app - to run a command through
# jupyterlab-commands-toolkit (jupyter-ai-tools@4d1c823 `jupyter_ai_tools/utils.py`, `rtc_available`).
# Gated after the strip, like the extensions above; mcp_bearer_probe.py says what the port must refuse.
COPY mcp_lazy_import_probe.py mcp_bearer_probe.py /tmp/
RUN jupyter server extension list 2>&1 | tee /tmp/extensions.txt \
 && for module in jupyter_server_mcp jupyter_ai_tools jupyterlab_commands_toolkit; do \
      grep -Eq "$module .*enabled" /tmp/extensions.txt \
   && grep -Eq "$module [0-9.]+ .*OK" /tmp/extensions.txt \
   || { echo "server extension $module is not enabled and OK" >&2; exit 1; }; \
    done \
 && python3 /tmp/mcp_lazy_import_probe.py \
 && python3 /tmp/mcp_bearer_probe.py \
 && rm -f /tmp/extensions.txt /tmp/mcp_lazy_import_probe.py /tmp/mcp_bearer_probe.py

# HOME must exist inside the rootfs: jupyter_server writes runtime state there,
# and on device it is not /root.
#
# `/root/.local` is the site hook's own residue, removed here because every `python3` RUN above
# creates root's user site: a Space's HOME is /home/jupydroid and its user site lives under
# `pkgs/`, so the image ships no user site at all (ADR-0164).
RUN mkdir -p /home/jupydroid /tmp/spaces \
 && rm -rf /root/.local
ENV HOME=/home/jupydroid

# Last, so no RUN above is served by a shell this line replaced: the image's own busybox becomes
# the one built without FEATURE_SUID. The 304 applet symlinks are untouched and still resolve,
# because that build uses Alpine's own config for the same version.
#
# This is the binary the device runs: under proot a Space's `/` is its own directory
# (ADR-0073), so every applet execs this file through the image's own links. If it carried
# check_suid, every command in every Space would still die of SIGSYS on the first dispatch.
COPY --from=busybox-builder /tmp/busybox/busybox /bin/busybox
