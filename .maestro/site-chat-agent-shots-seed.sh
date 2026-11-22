#!/usr/bin/env bash
# What .maestro/site-chat-agent-shots.yaml needs that no tap makes: an installed chat harness.
# The Space's Space Settings offers the install, but it downloads inside proot for minutes; the
# harness is one static binary, so this seed fetches the same pinned tarball the shipped install
# script names (app/src/main/assets/chat-harnesses/opencode.sh) and drops the binary where that
# script would leave it - the Space rootfs is app-private storage run-as can write.
#
# Pushed rather than installed through the UI because the picker's own install is a Space
# Settings surface the Spaces page already photographs; what this flow shoots is the Chat's
# controls once a harness answers, and a 195 MB download per theme pass would only add minutes.
# Run by scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

VERSION="1.19.3"
PACKAGE="opencode-linux-arm64-musl"
TARBALL="${SITE_OPENCODE_TARBALL:-/tmp/$PACKAGE-$VERSION.tgz}"
if [ ! -f "$TARBALL" ]; then
  curl -fsSL -o "$TARBALL" "https://registry.npmjs.org/$PACKAGE/-/$PACKAGE-$VERSION.tgz"
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
tar -xzf "$TARBALL" -C "$work" package/bin/opencode

"$ADB" push "$work/package/bin/opencode" /data/local/tmp/opencode-site >/dev/null
"$ADB" shell "run-as $APP sh -c 'mkdir -p files/spaces/base/usr/local/bin && cp /data/local/tmp/opencode-site files/spaces/base/usr/local/bin/opencode && chmod 755 files/spaces/base/usr/local/bin/opencode'"
"$ADB" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode"
