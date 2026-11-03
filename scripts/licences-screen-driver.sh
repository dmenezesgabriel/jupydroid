#!/usr/bin/env bash
# The gate: Settings → Licences lists the five documents the installed APK ships, and each opens
# as text from the build's own assets - not only the unit and instrumented proof that a fake port
# renders (`LicencesViewModelTest`, `LicencesScreenTest`).
#
# Runs no cell, so no device rule names a device this needs. It is run on the arm64 tablet because
# the source offer's links need a browser, which the emulator does not carry; and a cold emulator on
# a shared host raised SystemUI ANR dialogs that no flow of ours caused.
#
# Seeds no notebook, so it owes no `reset_notebooks`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/licences-screen"
mkdir -p "$EVIDENCE"

require_installed_app

run_flow "$ROOT/.maestro/licences-screen.yaml" "$EVIDENCE" || exit 1
