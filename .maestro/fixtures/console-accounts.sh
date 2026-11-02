#!/bin/sh
# Runs in base's terminal: whether opencode is signed in to its console, appended to
# jd058-console.txt as `<label>=some` or `<label>=none`, and nothing of the account.
#
#   sh jd058-console-accounts.sh signed-out
#
# `opencode console orgs` exits 0 signed out as well, printing "No accounts found" (opencode 1.19.3,
# measured on SM-X216B), so its exit code alone cannot tell; signed in is exit 0 without that line.
OUT="$PWD/jd058-console.txt"
ORGS="$(opencode console orgs 2>&1)" && ! echo "$ORGS" | grep -q "No accounts found" && ACCOUNTS=some
echo "$1=${ACCOUNTS:-none}" >>"$OUT"
