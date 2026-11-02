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
token, and no personal email address in any file, commit message or commit identity of any commit. The
clone follows open-source convention: no `evidence/` in any commit, and no code, test, script name or flow
name that cites an ephemeral id. What a result needs to be reproduced ships as a tested script.

## Demo

The closing run, from a cold start on a fresh clone of the recreated remote:

1. `git clone git@github.com:dmenezesgabriel/jupydroid.git "$(mktemp -d)"`.
2. `python3 scripts/read_history_markers.py` prints `0` for every class and exits 0.
3. `git log --all --format='%an <%ae>|%cn <%ce>' | sort | uniq -c` prints one line, the noreply identity.
4. `git ls-remote origin` lists `HEAD`, `refs/heads/main` and the `archive/p2v-declined-attempt` tag.
5. `git log --all --name-only --format= | grep -c '^evidence/'` prints `0`.
6. `python3 scripts/check_doc_citations.py` passes with drivers and flows in scope, and
   `git ls-files scripts .maestro | grep -cE '/(jd|aud|ops|p[0-9]|x[0-9]|u[0-9])[-_]'` prints `0`.

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
| tracked evidence | `git ls-files evidence \| wc -l`; on disk `du -sh evidence` | 7388 files, 52 MB; 2.3 GB on disk |
| home-path commits outside `evidence/` | `git log --all -S"$HOME" --oneline -- . ':!evidence' \| wc -l` | 7 (of 462) |
| carrier paths outside `evidence/` | `grep -vc '^evidence/' <carrier paths file>` | 0 (of 105) |
| step-coded script and flow names | `git ls-files scripts .maestro \| grep -cE '/(jd\|aud\|ops\|p[0-9]\|x[0-9]\|u[0-9])[-_]'` | measured at the rename task |
| ids in code and tests | `git grep -cE '\b(JD\|AUD\|OPS)-[0-9]{3}\b' -- app extension-api extensions tools scripts` | 141 in 35 files |

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
  row is edited at T13.
- **Evidence is local-only (owner's decision, 2026-11-02).** `evidence/` is gitignored, untracked, and
  removed from every commit by the rewrite (`--path-glob 'evidence/*' --invert-paths`). Drivers still write
  there; nothing tracked reads from or cites it. It is untracked *before* the in-place rewrite, because
  `git filter-repo` ends in `git reset --hard` and would delete files the new tip no longer tracks; it is
  also archived to a disk path outside the repository first. This supersedes `AGENTS.md` rule 2's
  "commit them", in an ADR of its own.
- **Reproduction is a script, not a README.** A result someone must be able to reproduce ships as a
  tested script whose docstring says how to run it. The SHA remap that T3 dry-ran as a pasted snippet is
  one. Measurements go in the commit body.
- **Ids are never cited in code (owner's decision).** `docs/backlog.md` and `docs/prd/` stay tracked as
  planning documents; code, tests, script names and flow names never carry a `JD-`, `AUD-`, `OPS-` or
  step code. The 339 step-coded drivers and the step-coded flows are renamed for what they demonstrate and
  kept, and `check_doc_citations.py` loses its driver and flow exemption, which existed only to tie a
  driver to its evidence directory.
- **Ids stay in old commits.** The rewrite scrubs what must not be published; it does not rename files
  or edit comments in history. Ids in past commits are not secret, and rewriting them would make the
  rewrite a refactor of 1,492 commits.

## Surface

| What | Files |
|---|---|
| measurement | `scripts/read_history_markers.py` (+ `_test.py`) |
| rewrite | `scripts/rewrite_history.py` (+ `_test.py`) |
| SHA remap | `scripts/remap_cited_shas.py` (+ `_test.py`) |
| evidence policy | `.gitignore`, `AGENTS.md`, `prompt-execution.md`, `docs/README.md`, `docs/adr/0132-*`, `scripts/testdata/` |
| renames | `scripts/*-driver.sh`, `.maestro/*.yaml`, `scripts/check_doc_citations.py` |
| decision | `docs/adr/0133-*`; ADR-0015's status line points to it |
| ledger | `docs/backlog.md` |

Comments under `app/` lose their ids at T8, nothing else there changes. Rehearsal numbers go in commit bodies,
written with `$HOME`, never the literal. `.sensitive-strings.local` is input only: never printed, quoted or
committed.

## Tasks

One TDD unit each, one conventional commit each.

- [x] T1 — `scripts/read_history_markers.py`: per-class counts over all history (home path in blobs, in
  messages and in file names; the paths of `token-carrier-paths.txt` that ever had a credential added).
  Exits non-zero on any hit. The identity is not a class: one is expected afterwards, which is not a
  zero, so Demo step 3 reads it with `git log`. Red test first, on a fixture repo built in a tmp dir.
- [x] T2 — `scripts/rewrite_history.py`: the rules file (`token-carriers.txt`, `.sensitive-strings.local`,
  `read_personal_data.py --scrub-rules`), the run-time mailmap, and the `git filter-repo` argv. One rules
  file feeds both `--replace-text` and `--replace-message`; `--prune-empty never`. Builders unit tested, one
  fixture-repo test runs it end to end. The rehearsal found that `--replace-text` skips binary blobs, so
  `*.pyc` is dropped from history (`--path-glob '*.pyc' --invert-paths`).
- [x] T3 — rehearsal: `git bundle create` outside the repo on a disk filesystem, `git bundle verify`,
  clone it, run T2, run T1 (all zero), diff the old and new tip trees (only scrubbed paths differ), commit
  count unchanged, tag present, SHA remap dry-run. All four classes read 0 on `9529c361`, deterministic
  across two runs, 219 cited SHAs unambiguous. **Superseded by T10**, which rehearses the argv T4 and T5
  change; its README goes with `evidence/`.
- [x] T4 — the rewrite's inputs leave `evidence/`: `rewrite_history.py` builds the carrier rules from
  `TOKEN_CARRIERS` instead of `token-carriers.txt`, and adds `--path-glob 'evidence/*'` beside `*.pyc`;
  `read_history_markers.py` replaces the paths-file class with two that need no list, "paths under
  `evidence/`" and "commits adding a credential anywhere". Red tests first.
- [x] T5 — evidence local-only: `/evidence/` in `.gitignore`, `git rm -r --cached evidence` (files stay on
  disk), and a new ADR that evidence is local and reproduction is a tested script, which `AGENTS.md`
  (rules 2 and 3, the procedure, the tooling rows), `prompt-execution.md` and `docs/README.md` follow.
  ADR-0001's and ADR-0015's status lines point to it. Check the ten `_test.py` files that name `evidence/`
  build their own fixtures and read nothing from the real directory. Three did, `check_chrome_parts`,
  `read_chrome_parts` and `read_node_fill`, so their frames moved to `scripts/testdata/` (owner's choice).
  The ADR is ADR-0132, and the rewrite's ADR becomes ADR-0133.
- [ ] T6 — docs cite no evidence path: the 60 lines in `docs/`, the ADRs, `AGENTS.md`, `README.md` and
  `pyproject.toml` restate the fact or name the script that reproduces it; a gate keeps a tracked
  Markdown file from naming `evidence/<dir>/`.
- [ ] T7 — drivers and flows renamed for what they demonstrate (339 `scripts/*-driver.sh`, the step-coded
  `.maestro/*.yaml`), every caller and cross-reference updated, exact duplicates merged, header comments and
  echo lines without ids. Split by prefix family at the start if it does not fit one commit.
- [ ] T8 — ids out of code: `check_doc_citations.py` loses its driver and flow exemption and learns
  `AUD-NNN`; the 141 id matches in code and tests and whatever the wider scope surfaces are restated.
  Red test first for each new rule.
- [ ] T9 — `scripts/remap_cited_shas.py`: from a `commit-map`, rewrite every 7 to 12 hex token in tracked
  text that prefixes exactly one old commit; refuse an ambiguous prefix with its value; `--dry-run` prints
  counts. Red tests first, on a fixture map.
- [ ] T10 — rehearsal again on the T4 argv: T3's checks, plus no path under `evidence/` in history, T9's
  dry run, and an in-place run on a copy of the working directory whose ignored `evidence/` must survive.
  Numbers in the commit body.
- [ ] T11 — the real rewrite, in place (`--force`, since `.git/filter-repo/already_ran` exists). **Waits
  for the owner's go.** `evidence/` archived outside the repository first. Then T9 remaps own SHAs cited
  in tracked files from `.git/filter-repo/commit-map`. Repo-local `user.email` is set to the noreply
  address, `origin` is re-added, T1 prints zero, the full gate is green.
- [ ] T12 — remote: the owner deletes and recreates the repository and turns on GitHub's "Block command
  line pushes that expose my email"; the agent pushes `main` and the tag; the Demo above is run.
- [ ] T13 — close: ADR-0133, ADR-0015's status line, the backlog row edited to `shipped` with the marker
  moved to `OPS-003`, this PRD deleted.

## Out of scope

The licence and notices (`OPS-003`), the README (`OPS-004`), flipping visibility and branch protection
(`OPS-005`), and CI (`OPS-006`).
