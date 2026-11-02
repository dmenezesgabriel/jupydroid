#!/usr/bin/env bash
# The Chat panel lists the workspace's chats, opens the one tapped, goes back to the list for
# another, and keeps the open chat while another panel is shown.
#
#   nice -n 10 timeout 1800 scripts/chat-several-list-driver.sh
#
# Exits non-zero if the flow fails.
#
# TABLET ONLY (SM-X216B): each chat is read from its file by a running Space's server, which only
# arm64 ships. No agent is asked, so opencode need not be installed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-several-list"

trap restore_rotation EXIT

# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/chat-several-untitled.chat" "untitled.chat"
seed_file "$ROOT/.maestro/fixtures/chat-several-notes.chat" "notes.chat"
# A file that is not a chat, which the list must leave out.
README="$(mktemp)"
printf 'not a chat\n' >"$README"
seed_file "$README" "readme.md"
rm -f "$README"
rotate_to 1

run_flow "$ROOT/.maestro/chat-several-list.yaml" "$EVIDENCE"
