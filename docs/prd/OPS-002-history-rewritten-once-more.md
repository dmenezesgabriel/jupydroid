# OPS-002 — History rewritten once more, then kept clean

Milestone: `M9` · State: open · Depends on: `AUD-013` · Closes: —

> An **OPS** item, written from `_TEMPLATE.md` as far as it applies
> ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): nothing on screen
> changes, so there is no Maestro demo and no reference table. It closes on the run named under *Demo*.
> Every line of code it writes is still TDD.
>
> **Deleted when the item ships** ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A stranger who clones the repository, once it is public, finds none of the host's home path, no server
token, and no personal email address in any file, commit message or commit identity of any commit.

## Demo

The closing run, from a cold start on a fresh clone of the recreated remote:

1. `git clone git@github.com:dmenezesgabriel/jupydroid.git "$(mktemp -d)"`.
2. `python3 scripts/read_history_markers.py` prints `0` for every class and exits 0.
3. `git log --all --format='%an <%ae>|%cn <%ce>' | sort | uniq -c` prints one line, the noreply identity.
4. `git ls-remote origin` lists `HEAD`, `refs/heads/main` and the `archive/p2v-declined-attempt` tag.

The item is not `shipped` until this has been run and its output named in the commit body.

## What is examined, measured at the start

Reproduce from the repository root:

| What | Command | Value |
|---|---|---|
| commits whose diff adds or removes the home path | `git log --all -S"$HOME" --oneline \| wc -l` | 462 |
| message lines carrying it | `git log --all --format=%B \| grep -c "$HOME"` | 5 |
| commits | `git rev-list --all --count` | 1488 |
| identities | `git log --all --format='%an <%ae>\|%cn <%ce>' \| sort \| uniq -c` | one, on every commit |
| token carriers | `evidence/aud-013-scrub-list/token-carriers.txt` | 8 rules, 105 carrier paths |
| remote | `gh repo view dmenezesgabriel/jupydroid --json visibility,forkCount` | private, no forks, no PRs |

## Decisions

- **Identity: the GitHub noreply address, name kept.** Every commit is rewritten to
  `Gabriel Menezes <50274255+dmenezesgabriel@users.noreply.github.com>` through a mailmap. A pseudonymous
  name would disagree with the docs and ADRs, which carry the real one. The old address is also the
  profile's public email, so this stops a bulk harvest from a clone and nothing more. The mailmap is
  generated at run time, never tracked: a tracked one would hold the address it removes.
- **Remote copy: deleted and recreated.** A force-push moves the refs but leaves the old objects
  fetchable by SHA until GitHub collects them. The repository is private with no fork, so nothing else
  holds them. The owner deletes and recreates it; the agent never runs the delete.
- **The rule that supersedes ADR-0015's clause.** A rewrite is affordable while the repository is private
  and shared with nobody, and never after it is public, where the answer is rotation. A later ADR states it.
- **Kept on purpose.** The upstream author's address in the MIT notice of `tools/grammars/python/`
  (the licence requires it), and its one mention in a commit message. The `archive/p2v-declined-attempt`
  tag is rewritten with everything else.
- **The backlog row's "machine-specific setup moved to a gitignored local file" is already settled.**
  `AUD-013` found the script derives the path, so nothing would read such a file. The clause goes when the
  row is edited at T6.

## Surface

| What | Files |
|---|---|
| measurement | `scripts/read_history_markers.py` (+ `_test.py`) |
| rewrite | `scripts/rewrite_history.py` (+ `_test.py`) |
| rehearsal evidence | `evidence/ops-002-rehearsal/README.md`, written with `$HOME`, never the literal |
| decision | `docs/adr/0132-*`; ADR-0015's status line points to it |
| ledger | `docs/backlog.md` |

Nothing under `app/`. `.sensitive-strings.local` is input only: never printed, quoted or committed.

## Tasks

One TDD unit each, one conventional commit each.

- [ ] T1 — `scripts/read_history_markers.py`: per-class counts over all history (home path in blobs, in
  messages and in file names; each path of `token-carrier-paths.txt` via `-S`; distinct identities). Exits
  non-zero on any hit. Red test first, on a fixture repo built in a tmp dir. Baseline run on this repo.
- [ ] T2 — `scripts/rewrite_history.py`: the rules file (`token-carriers.txt`, `.sensitive-strings.local`,
  `read_personal_data.py --scrub-rules`), the run-time mailmap, and the `git filter-repo` argv. One rules
  file feeds both `--replace-text` and `--replace-message`; `--prune-empty never`. Builders unit tested, one
  fixture-repo test runs it end to end.
- [ ] T3 — rehearsal: `git bundle create` outside the repo on a disk filesystem, `git bundle verify`,
  clone it, run T2, run T1 (all zero), diff the old and new tip trees (only scrubbed paths differ), commit
  count unchanged, tag present, SHA remap dry-run. Numbers in `evidence/ops-002-rehearsal/README.md`.
- [ ] T4 — the real rewrite, in place (`--force`, since `.git/filter-repo/already_ran` exists; in place so
  the ignored evidence on disk survives). **Waits for the owner's go.** Then own SHAs cited in tracked files
  are remapped from `.git/filter-repo/commit-map`, matching 7 to 12 hex by prefix (an 8-hex abbreviation
  is in the set), with one command named in the commit body. Repo-local `user.email` is set to the noreply
  address, `origin` is re-added, T1 prints zero, the full gate is green.
- [ ] T5 — remote: the owner deletes and recreates the repository and turns on GitHub's "Block command
  line pushes that expose my email"; the agent pushes `main` and the tag; the Demo above is run.
- [ ] T6 — close: ADR-0132, ADR-0015's status line, the backlog row edited to `shipped` with the marker
  moved to `OPS-003`, this PRD deleted.

## Out of scope

The licence and notices (`OPS-003`), the README (`OPS-004`), flipping visibility and branch protection
(`OPS-005`), and CI (`OPS-006`).
