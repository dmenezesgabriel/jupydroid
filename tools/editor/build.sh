#!/usr/bin/env bash
# Builds the per-cell CodeMirror 6 editor into app/src/main/assets/editor/index.html.
#
#   tools/editor/build.sh
#
# npm ci, not npm install: the lockfile is committed so the bundle is reproducible, the same
# way tools/rootfs pins its image by digest. The output is generated and gitignored.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
[ -d node_modules ] || npm ci --no-audit --no-fund
node build.mjs
