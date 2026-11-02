#!/bin/sh
# Runs inside base's terminal, under proot: where the time goes when jupyter_server imports
# jupyter_ai_acp_client, the first extension package it imports (`Extension package
# jupyter_ai_acp_client took 1.2875s to import` in the server's log).
#
# jupyter_server.serverapp is imported first, so its own modules are already loaded and
# `-X importtime` attributes to jupyter_ai_acp_client only what it adds, as at server start. The
# first run may read from a cold page cache, so there are three.
#
# Each run's stderr lands in acp-importtime-<n>.txt beside this script, and acp-importtime.txt
# ends in `probe-done`, so the driver can tell a finished probe from a hung one.
OUT="$PWD/acp-importtime.txt"
: >"$OUT"

say() { echo "$*" >>"$OUT"; }

say "python=$(python3 --version 2>&1)"
for run in 1 2 3; do
  python3 -X importtime -c "import jupyter_server.serverapp; import jupyter_ai_acp_client" \
    2>"$PWD/acp-importtime-$run.txt"
  say "run-$run-rc=$?"
done
say "probe-done"
