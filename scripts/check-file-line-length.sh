#!/usr/bin/env bash
# Working Agreement (docs/PLAN.md, rule 6): no Kotlin file may exceed 500 lines.
# Fails on the files passed by pre-commit. Used by .pre-commit-config.yaml.
set -euo pipefail

max="${MAX_FILE_LINES:-500}"
status=0

for file in "$@"; do
  [ -f "$file" ] || continue
  lines=$(awk 'NF { n++ } END { print n + 0 }' "$file")
  if [ "$lines" -gt "$max" ]; then
    echo "check-file-line-length: $file has $lines non-blank lines (limit $max); split it." >&2
    status=1
  fi
done

exit "$status"
