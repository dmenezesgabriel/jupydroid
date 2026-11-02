#!/usr/bin/env bash
# The two `access-*-history-entry` keys reach code the document no longer holds.
#
# TABLET ONLY (SM-X216B). A kernel's input history is IPython's, recorded by the interpreter that
# ran the cell; on the x86_64 emulator `MainActivity` falls back to `FakeKernelGateway`, whose
# history is whatever this app happened to hand it in this process. The flow would still go green
# there and would be about the fake rather than about ipykernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/access-kernel-history"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/access-kernel-history.ipynb" "Kernel History"

# **IPython's history is a sqlite database under the kernel's HOME**, and `reset_notebooks` does
# not reach it: its `rm -rf ./*` does not match dotfiles, so `.ipython/profile_default/history.sqlite`
# survives every reset and carries entries from previous runs of this and any other flow.
#
# Cleared here rather than there, because it is this flow's concern alone: `get_tail` orders the
# *current* session's rows last (`IPython/core/history.py:916-975`, measured in the shipped
# rootfs), so every other flow is indifferent to what the file holds. Clearing it makes the walk
# below start from a kernel whose whole history is what this run put there.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -rf files/spaces/base/home/jupydroid/.ipython" || true

run_flow "$ROOT/.maestro/access-kernel-history.yaml" "$EVIDENCE" || exit 1

# Back to the app's own default, so no flow run after this one on the same device inherits a
# setting nobody chose for it - the same restore `kernel-history-setting` makes, and for its reason.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f files/editor-settings.json"
