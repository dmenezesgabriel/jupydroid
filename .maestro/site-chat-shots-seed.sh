#!/usr/bin/env bash
# The workspace .maestro/site-chat-shots.yaml shoots: three chats - one holding a finished
# exchange whose tool calls ran under different permission answers, one a plain exchange with
# two questions waiting in its queue, and notes.chat for the list's third row. The queued
# questions live in the app's own storage, files/chat-queues/base.json, keyed by chat path -
# run-as reaches it where seed_file's workspace root cannot.
#
# No chat harness is installed for this flow: with none listed the queue holds its questions
# (ChatPanel.askDue sends nothing to no agent), which is also the state a fresh install's Chat
# shows. A leftover opencode binary from site-chat-agent-shots would answer the first queued
# question and drain the pills this flow exists to photograph.
# Run by scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_file "$ROOT/.maestro/fixtures/site-chat-transcript.chat" "untitled.chat"
seed_file "$ROOT/.maestro/fixtures/chat-several-notes.chat" "notes.chat"
seed_file "$ROOT/.maestro/fixtures/site-chat-queued.chat" "untitled1.chat"

# base64 like seed_file's payload: adb shell can mangle stdin, and the JSON is one line.
queue='{"untitled1.chat":["And list the other files?","Export the summary to notes.md."]}'
payload="$(printf '%s' "$queue" | base64 -w0)"
"$ADB" shell "run-as $APP sh -c 'mkdir -p files/chat-queues && echo $payload | base64 -d > files/chat-queues/base.json'"

"$ADB" shell "run-as $APP rm -f files/spaces/base/usr/local/bin/opencode"
