# ADR-0015 — Rewrite history once, before the first push, to take the device out of it

Status: accepted; amended by [ADR-0132](0132-evidence-is-local-and-reproduction-is-a-tested-script.md), which keeps all of `evidence/` local, and by [ADR-0133](0133-history-is-rewritten-only-while-private-and-shared-with-nobody.md), which replaces "does not generalise" with the line between a rewrite and a rotation
Date: 2026-09-16

## Context

`AGENTS.md` rule 2 asks for `logcat` as evidence, and 223 full-device dumps went in — 249 MB at the
tip, 1.16 GB across history.

`adb logcat` is the **whole device's** log, not this app's. Read end to end, those dumps held the
home Wi-Fi's BSSID and SSID (a BSSID geolocates a router), Samsung FCM push tokens, LAN addresses,
and every other app's activity. The tablet's serial was in 385 more files, and in five file names.

A `.gitignore` could not help: every one of them was already in commits that a push would send.

## Decision drivers

- The repository had **no remote yet**, so a rewrite was still cheap and affected nobody.
- Evidence should prove the app, not the house it ran in.
- Nothing may be lost, and the rewrite must be auditable afterwards.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Add a `.gitignore` and move on | No rewrite | The data is in the existing commits; a push publishes it |
| Delete the files in a new commit | Simple, preserves history | Same — the blobs stay reachable |
| `git filter-repo` across all branches, once, before the first push | The data genuinely leaves the repository | Rewrites every SHA, which is why it is affordable only before a push |

## Decision

Before the repository had a remote, `git filter-repo` rewrote all four branches once:

- removed `evidence/*logcat*.txt`, `evidence/*/activities.txt`, the long-deleted
  `tools/editor/node_modules/` and `prompt.md`;
- replaced the tablet's serial with `TABLET_SERIAL`, in contents **and** in file names, from the
  gitignored `.sensitive-strings.local`;
- kept empty commits, so every branch has the commit count it had.

Rehearsed first on a clone of the backup bundle and diffed against the old tip: only those paths and
those replacements changed. Afterwards `git log --all -S` finds none of the markers in any commit.

Nothing was lost. The original history is a bundle outside the repository, the dumps are back on
disk as ignored files, and `.git/filter-repo/commit-map` maps every old SHA to its new one. SHAs
quoted in files were remapped with it, except one directory under `evidence/` that was named for a
SHA.

**`logcat` is captured but never committed.** `scripts/check_device_secrets.py` enforces it as a
pre-commit gate, reading its local literals from the gitignored `.sensitive-strings.local`. A README
quotes the lines it cites.

## Consequences

**This forbids** committing a device dump, or anything the dumps leaked.

**This is the one exception to "no rewriting history",** and it was only affordable because nothing
had been pushed. It does not generalise: after a push the same problem costs every collaborator a
re-clone, and the answer would be rotation of whatever leaked rather than a rewrite.

The thing a future reader will be tempted to "fix" is the gate's apparent paranoia about a file that
is merely a log. The file is the whole device's log, which is the entire point.
