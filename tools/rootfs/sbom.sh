#!/usr/bin/env bash
# Regenerates THIRD_PARTY.md from the arm64 rootfs tarball tools/rootfs/build.sh wrote.
#
#   tools/rootfs/sbom.sh [arm64-v8a]
#
# syft runs as a container pinned by digest (ADR-0123's rule for anything that decides what ships), so
# a newer syft cannot change the listing unseen: bumping SYFT_IMAGE is a reviewed edit. The tarball is
# extracted onto disk under build/ rather than /tmp, which is tmpfs on some hosts and would hold the
# whole rootfs in memory. `dev/*` is skipped because an unprivileged tar cannot create device nodes
# and syft reads packages, not nodes.
set -euo pipefail

ABI="${1:-arm64-v8a}"
[ "$ABI" = "arm64-v8a" ] || { echo "unsupported abi '$ABI'; expected arm64-v8a" >&2; exit 1; }

SYFT_IMAGE="anchore/syft@sha256:4cc92b64ca74ffca4c94d4c8fcc01f217731375300be14d0b6eeb2e82eaa110a"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT/build/rootfs"
TARBALL="$OUT_DIR/rootfs-$ABI.tzst"
EXTRACTED="$OUT_DIR/sbom-src"
SBOM="$OUT_DIR/sbom-$ABI.json"

[ -f "$TARBALL" ] || { echo "missing $TARBALL; run tools/rootfs/build.sh $ABI first" >&2; exit 1; }

rm -rf "$EXTRACTED"
mkdir -p "$EXTRACTED"
trap 'rm -rf "$EXTRACTED"' EXIT

echo "==> extracting $TARBALL"
zstd -dc "$TARBALL" | tar -x --no-same-owner --exclude='dev/*' -C "$EXTRACTED"

echo "==> cataloguing with $SYFT_IMAGE"
docker run --rm -v "$EXTRACTED:/rootfs:ro" -v "$OUT_DIR:/out" "$SYFT_IMAGE" \
  dir:/rootfs -o "syft-json=/out/sbom-$ABI.json"

echo "==> rendering THIRD_PARTY.md"
cd "$ROOT"
uv run --locked python scripts/render_third_party_listing.py \
  --sbom "$SBOM" --rootfs-sha256 "$(sha256sum "$TARBALL" | cut -d' ' -f1)" --out THIRD_PARTY.md
