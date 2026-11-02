#!/usr/bin/env bash
# The Chat panel's input, drawn and measured: the flow types a question and sends it, and this
# driver reads where Send sits against the field from the hierarchy the flow leaves behind.
#
#   nice -n 10 timeout 900 scripts/ai-chat-input-driver.sh <before|after>
#
# Run once on the build before the input changes and once on the build after, each into its own
# evidence subdirectory, so the two sets of numbers come from two APKs and not one. Exits non-zero
# if the flow fails or the hierarchy holds no field or no Send.
#
# TABLET ONLY (SM-X216B): Send is enabled only on an open chat, and only arm64 ships a Space whose
# server opens one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"
# shellcheck source=scripts/chat-reads.sh
. "$ROOT/scripts/chat-reads.sh"

EVIDENCE="$ROOT/evidence/ai-chat-input/${1:?before or after}"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

require_installed_app
mkdir -p "$EVIDENCE"
# Empties the workspace, `untitled.chat` with it, so the panel holds this run's question only.
reset_notebooks
rotate_to 1

# Before the flow, so that the scrub `run_flow` runs after it takes the serial out of device.txt too.
record_device "$EVIDENCE"
run_flow "$ROOT/.maestro/ai-chat-input.yaml" "$EVIDENCE"
print_input_geometry "$EVIDENCE"
