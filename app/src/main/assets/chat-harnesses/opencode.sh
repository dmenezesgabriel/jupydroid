#!/bin/sh
# Installs opencode into a Space for jupyter-ai-acp-client's opencode persona, which refuses at
# import unless `command -v opencode` finds the binary and `opencode --version` reports a version
# >=1.0.0,<2 (jupyter-ai-acp-client@77016f63530f `jupyter_ai_acp_client/acp_personas/opencode.py`).
# The image ships no Node, so the pinned linux-arm64-musl tarball - the build the Space-side probe
# measured - is fetched by the image's own python3 and certifi, and installed to /usr/local/bin,
# which SpaceRuntime puts on the Space's PATH
# (app/src/main/java/com/jupydroid/app/data/space/SpaceRuntime.kt).
set -eu

VERSION=1.19.3
TARBALL="https://registry.npmjs.org/opencode-linux-arm64-musl/-/opencode-linux-arm64-musl-$VERSION.tgz"

echo "fetching opencode $VERSION"
# python3 rather than busybox wget: the image's certifi is the CA bundle a Space already trusts.
python3 - "$TARBALL" /tmp/opencode.tgz <<'EOF'
import shutil, ssl, sys, time, urllib.request
import certifi
started = time.monotonic()
context = ssl.create_default_context(cafile=certifi.where())
with urllib.request.urlopen(sys.argv[1], context=context) as answer, open(sys.argv[2], "wb") as out:
    shutil.copyfileobj(answer, out)
print(f"fetch-seconds={time.monotonic() - started:.1f}")
EOF

rm -rf /tmp/opencode-pkg
mkdir -p /tmp/opencode-pkg
tar -xzf /tmp/opencode.tgz -C /tmp/opencode-pkg
install -m 755 /tmp/opencode-pkg/package/bin/opencode /usr/local/bin/opencode
rm -rf /tmp/opencode-pkg /tmp/opencode.tgz

# The script's own verdict: the persona's first question is `which opencode`, its second is
# `opencode --version` - bounded, so a hung binary cannot hold the install open.
path=$(command -v opencode 2>/dev/null) || {
  echo "opencode is not on PATH after install" >&2
  exit 1
}
echo "installed $path"
timeout -s KILL 15 opencode --version
