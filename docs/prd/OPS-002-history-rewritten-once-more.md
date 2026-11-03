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
   `git ls-files scripts .maestro | grep -cE '/((jd|aud|ops)-[0-9]{3}|[a-z]{1,4}[0-9]+[a-z]*[0-9]*)[-_][^/]*$'`
   prints `0`.

The item is not `shipped` until this has been run and its output named in the commit body.

## What is examined, measured at the start

Reproduce from the repository root:

| What | Command | Value |
|---|---|---|
| commits whose diff adds or removes the home path | `git log --all -S"$HOME" --oneline \| wc -l` | 462 |
| message lines carrying it | `git log --all --format=%B \| grep -c "$HOME"` | 5 |
| commits | `git rev-list --all --count` | 1488 |
| identities | `git log --all --format='%an <%ae>\|%cn <%ce>' \| sort \| uniq -c` | one, on every commit |
| token carriers | `TOKEN_CARRIERS` in `scripts/check_device_secrets.py` | 8 rules, 105 carrier paths |
| remote | `gh repo view dmenezesgabriel/jupydroid --json visibility,forkCount` | private, no forks, no PRs |
| tracked evidence | `git ls-files evidence \| wc -l`; on disk `du -sh evidence` | 7388 files, 52 MB; 2.3 GB on disk |
| home-path commits outside `evidence/` | `git log --all -S"$HOME" --oneline -- . ':!evidence' \| wc -l` | 7 (of 462) |
| carrier paths outside `evidence/` | `grep -vc '^evidence/' <carrier paths file>` | 0 (of 105) |
| step-coded script, flow and fixture names | basenames of `git ls-files scripts .maestro` matching `^((jd\|aud\|ops)-[0-9]{3}\|[a-z]{1,4}[0-9]+[a-z]*[0-9]*)[-_]` | 1,094 (jd 563, p 418, step 47, u 29, a 18, aud 10, x 9); about 1,900 references; 56 targets collide once stripped |
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

Rehearsal numbers go in commit bodies,
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
- [x] T6 — docs cite no evidence path: the 60 lines in `docs/`, the ADRs, `AGENTS.md`, `README.md` and
  `pyproject.toml` restate the fact or name the script that reproduces it; a gate keeps a tracked
  Markdown file from naming `evidence/<dir>/`.
- T7 — drivers, flows and fixtures renamed for what they demonstrate: the leading code and any `-t<n>` or
  `-part<n>` segment stripped, a collision or a name left meaningless named by hand, every caller and
  cross-reference updated, exact duplicates merged, the renamed drivers' header lines, `echo` lines and
  `OUT=` directory without ids. 1,094 files and about 1,900 references, so split by prefix family:
  - [x] T7a — `scripts/rename_tracked_files.py` (+ `_test.py`): `propose` prints the old→new mapping and
    refuses a collision with both values, `apply` runs `git mv` and rewrites references longest name first
    on a name boundary. Red tests first. Applied to the `aud`, `x`, `u` and `a` families (66 files).
    References are rewritten by **stem**, since a flow is cited as `a1-markdown` as often as by file name;
    an evidence directory named after a step (`audit-a4-…`) is left to the driver's hand edit. Sub-flows
    that a bare strip leaves meaningless take their driver's name (`text-history-typed.yaml`,
    `adaptive-one-pane.yaml`).
  - [x] T7b — `step*` (47 files). Named by hand where a bare strip said nothing or would clash: a driver
    stem is matched as a substring of a typed argument (`run-all-flows.sh`), so `editor`, `brackets`,
    `completion` and `line-numbers` each sit inside a name a later slice produces and became
    `editor-renders-seeded-cells`, `bracket-match-highlight`, `completion-popup` and
    `gutter-wrapped-line`; the step 2 and 3 flows became what they show. The theme driver's evidence
    directory takes the driver's name. Plan-step prose inside flow bodies is left to T8.
  - [x] T7c — `p1*` to `p4*` (255 files, 12 of them past the tool's collisions). Named by hand where the
    strip collided or said nothing: the `partN` sub-flows of `p1b`, `p1c`, `p1d`, `p1g` and `p4k` take their
    driver's name, as in T7a, and so do the two halves of the terminal resize, IME resize and Ctrl-C gates; the
    two `search-options` became `find-options` and `workspace-search-options`. A driver stem that is a
    substring of another driver's (`replace` inside `replace-all`, `rotation` inside the later
    `jd-030-rotation-*`) was qualified, and the substring check over the final set prints nothing.
    Hand edits the tool does not make: the plan-step id in the driver header lines, `echo` lines and
    evidence directory names, and the partial stems in comments (`p1d-part2`, `p2f-2's`, `p2b-split`).
    Plan-step prose inside driver and flow bodies, and the file names a flow types on the device (`p4k-marker-*`,
    `p2j-5-share`), are left to T8.
  - [x] T7d — `p5*` to `p8*` (163 files: 31 fixtures, 71 flows, 61 drivers; 418 `p` files in total across
    T7c and T7d). Named by hand where the strip collided or said nothing: the two debugger `stopped-settled`
    flows (their files differ) say which stop they settle, the `p8d`/`p8e` first/second fixtures take their
    flow's name, and bare `stop`, `step`, `kernel`, `marker`, `back` took the driver's. Every driver stem inside
    another's, or inside a `jd-*` name T7e to T7g will strip, was qualified (`format-cell`, `rename-symbol`,
    `launcher-from-chrome`, `kernel-picker-tablet`, `running-panel-wide`, `symbol-toggle-setting` and others);
    the substring check over the final set prints nothing. Evidence directories take the driver's name, the two
    tabs drivers sharing `tabs`. Left to T8 as in T7c: plan-step prose in bodies, names a flow types on the
    device (`p8d-alpha`, `p8f-second.ipynb`, `p7e2-pip.txt`) and kernelspec names (`p8g-echo`, `p8g` in Kotlin tests).
  - [x] T7e — `jd-001` to `jd-019` (210 files: 25 fixtures, 129 flows, 56 drivers). Named by hand where the
    strip collided or said nothing: a `tN` driver takes what its header shows, not the task number; the
    whole-demo driver of `jd-003`, `jd-005` and `jd-009` became `*-demo`, and `console-clear` and
    `markdown-output` were qualified, all for the substring rule (the check over the final stems finds no
    pair with a T7e name inside another). Three flow pairs that differ in content are named by what each
    asserts, and seven fixtures that said nothing alone take their driver's name. A driver that builds a
    flow name from a prefix (`$FLOWS-part2-x.yaml`) is beyond the stem rewrite: its prefix was re-pointed
    and `run_part` expands `${1#part*-}`, and each driver was checked to resolve the flows its old self
    did. Left to T8: item ids in flow header comments and driver bodies (`JD-002 demo, step 5`) and the
    names a flow types on the device.
  - [x] T7f — `jd-020` to `jd-039` (226 files: 36 fixtures, 133 flows, 51 drivers, 6 sourced helpers; 225
    paths after the two identical `notes.py` fixtures merged). Named by hand where the strip collided: 22 of
    the 23 collision groups differ in content, so each `tN` driver and its flows take what the task showed
    (`gfm-table`, `rendered-web-link`, `text-output-url`, `markdown-images-attachment`,
    `large-image-output-wide`, `atomic-save-notebook`, `state-write-settings`, `save-conflict-notebook`,
    `rotation-dirty-tab` and their siblings), and the `partN` segment is dropped from flows. Nine single-task
    drivers that were the stem of their own demo (`save-on-demand`, `tab-reload`, `not-text` and six more)
    were qualified by what they show (`-running-row`, `-icon`, `-refused` and the like), for the substring rule; the check over
    the final stems finds no pair with a T7f name as the inner one. The stem rewrite also turned a
    `FLOWS` prefix into a bare fixture stem (`save-conflict`) and rewrote the tool's own docstring example;
    both were repaired by hand. Fixtures the ruff hooks had never linted failed when the rename staged them:
    `mod.py` stays one line (flows tap `~/mod.py:1`), so `pyproject.toml` exempts it with its reason, and the
    disk-writer fixture was formatted. Left to T8: item ids in flow header comments and driver bodies
    (`jd-023's flow`, the theme drivers' usage lines) and the names a flow types on the device. T7g must
    qualify `jd-043-tab-demo`, since `chat-in-tab-demo` already contains `tab-demo`, and `notes.py`,
    `target.ipynb` and `counter.ipynb` are now taken fixture names.
  - [x] T7g — `jd-040` to `jd-074` (127 files: 17 fixtures, 69 flows, 39 drivers, 2 sourced helpers; 563 `jd`
    files across T7e to T7g, none left tracked). Named by hand where the strip collided: `checkpoint-revert`
    and `checkpoint-follows-file`, `git-panel-branch` and `git-panel-changed-files`, the `ai-chat-explore`
    flows by orientation (`-landscape`, `-portrait`), the `ai-chat-keeps-server` flows by what each does
    (`-opened`, `-still-open`, `-idle-restored`), and the two `other.py` and `notes.md` fixtures by their first
    user (`checkpoint-other.py`, `tab-copy-other.py`, `kept-notes.md`). Qualified for the substring rule:
    `ai-chat-streamed-answer`, `save-all-open-tabs`, `save-as-new-name`, `tab-close-siblings-dirty` and
    `tab-menu-demo` (not `tab-delete-…`, which would contain the `tab-delete` driver); the check over the final
    stems finds no T7g name inside another. Hand edits as in T7c to T7f: each driver's evidence directory takes
    its own name (the stem rewrite had pointed every `ai-chat` driver at the renamed base one), the `t3`/`t4d`
    argument defaults became `run`, the `FLOWS` prefixes of four drivers and the `ai-chat-keeps-server`
    `run_part` were re-pointed, and the device temp files lost their ids; every path a changed file names
    resolves to a tracked file. Left to T8: item ids in comments (`JD-069's demo`, `jd-023's flow`, the
    `jd-046`/`jd-058` helper headers), the item-coded evidence directories (`jd-032`, `jd-062`, `jd-063`, `jd-064`) that
    drivers no slice renamed still write to, `chat-server`'s device file `t2.chat`, and the `part N failed` messages.
- T8 — ids out of code: `check_doc_citations.py` loses its driver and flow exemption and learns
  `AUD-NNN`; every plan-step label, item id and `evidence/<dir>` a comment cites is restated so it stands
  alone. Red test first for each new rule. The gate passes today only because it never reads `scripts/` or
  `.maestro/`: widened, it reports 781 comment hits in 369 files (172 in `*-driver.sh`, 426 in
  `.maestro/*.yaml`, 73 in `check_*.py`, 68 in `read_*.py`, 42 in the other `scripts/` files). `app/`,
  `extensions/` and `tools/` already carry none. So each slice adds its own glob to `SCANNED_FILES` and
  cleans that family, which the commit hook then keeps clean; the last slice collapses the globs into
  `SCANNED_TREES`. A prefix label is dropped; a sentence that leans on an id is rewritten to state the
  fact. The gate's own docstrings and tests describe a banned pattern in words and keep their examples in
  string literals, which no scanner reads.
  - [x] T8a — the gate learns `AUD-NNN` (the `JD` and `OPS` entries become one pattern), and
    `scripts/read_*.py` joins its scope: 68 hits in 23 files, now 0. An audit slice is named by what it
    examined ("the terminal slice") rather than by id; a pointer into `evidence/` or into a dropped commit
    states the fact; `reference/` became "the gitignored `reference` directory", since a trailing slash
    is what the pattern matches. The ledger tools keep `AUD-NNN` in string literals, which they parse
    and no scanner reads. T8b rewrites the gate's own docstring, which names `reference/` and `evidence/`.
  - [x] T8b — `scripts/check_*.py` and the other `scripts/*.py` and `*.sh` (117 hits: 115 banned
    tokens plus two unknown-ADR mentions in the gate's own docstring), now 0, and the gate's "Out of
    scope on purpose" paragraph rewritten as "Not yet scanned". `scripts/*.py` is the glob; the
    non-driver `.sh` name themselves where a prefix glob would match a `*-driver.sh` (`atomic-`,
    `run-`). A step code's fact is stated in words ("the busybox the app first shipped", "the
    notebooks path moved once"); a pointer into `evidence/` names the class ("an evidence
    directory", "the evidence tree"); a docstring example that would hold a banned literal shows the
    clean case and names the test that holds the failing one (`CitationsInTest`, `UnknownAdrsTest`,
    `CitesRetiredTest`). Two names no pattern matches went with it: `t1-pty-gate` in a test
    docstring became `cold-path-driver.sh`, and the `u5 step4` usage example became real driver
    substrings.
  - [x] T8c — `scripts/*-driver.sh` from `a` to `l` (202 files, 72 hits in 39), now 0, with the
    functional names those drivers carry: 6 item-coded evidence roots take the driver's name
    (`x2a-explorer-browse` -> `explorer-browse`, `jd-063-chat-chips` -> `chat-chips-full-width`),
    13 `tN` evidence subdirectories take the run's (`t1-table` -> `table`, `t2` -> `tall`), and
    `t2.chat`, `/sdcard/p2n.xml`, `P8A_EVIDENCE_ROOT` and `JD026_LARGE_LINES` lose their codes.
    The `tN` rename is the owner's decision: a task ordinal in an evidence subdirectory is a run
    label, not a step code, so T8d follows it for the `p*` to `z*` drivers. 17 more files lose the
    lowercase and undashed ids no gate pattern matches (the "PRD's demo" headers, `T1`/`T3`,
    `jd-002`, `jd-023`, `p1d`, `p8g`, `x2b`, "Audit A4", "Step N"), and a sentence that leaned on
    one names the sibling driver instead. Names a flow types on the device (`jd046-probe.sh`,
    `p4k-marker-*`, `p5b-*`, `P8g …`, `p8g-echo`) are left to T8e/T8f, which own the flows that
    type them (owner's decision). `scripts/[a-l]*-driver.sh` is the glob.
  - [x] T8d — `scripts/*-driver.sh` from `m` to `z` (200 files, 100 hits in 50), now 0, with the
    functional names those drivers carry: 5 item-coded evidence roots take the driver's name
    (`jd-062-recents-arrives-collapsed` -> `recents-persists`, `jd-064-rootfs-advisories` ->
    `rootfs-advisories-demo`, `x2e-home-new-python-file` -> `new-python-file`), 15 `tN` evidence
    subdirectories take the run's (`t1-web-link` -> `web-link`, `t2` -> `file`), and the five device
    temp files lose their codes (`/sdcard/p2o3-hierarchy.xml` -> `pane-widths-hierarchy.xml`,
    `/data/local/tmp/jd015.data` -> `output-caps.data`). 37 more files lose the lowercase and
    undashed ids no gate pattern matches (the "PRD's demo" headers, `T1`/`T2`/`T3`, `A2`, `M5`,
    `a1c`, `p1g`, `p1b`, `p2l-1`, `p2z`, `p2h-2`, `p4j-3b`, `p4k`, `jd-002`). Names a flow types on
    the device (`p5a-version.txt`, `p8f-second.ipynb`, `p4j2-after.txt`, `p8b-install.log`) stay for
    T8e/T8f, which own the flows that type them, as T8c left them. `scripts/[m-z]*-driver.sh` is the
    glob.
  - [x] T8e1 — `.maestro/[a-c]*.yaml` (192 of the 690 flows, 94 hits in 49 of them, plus 21 ids no
    gate pattern matches in 19 flows, 12 of those not among the 49), now 0. A header prefix is
    dropped; a reference to an earlier step states its fact or names the sibling flow or driver
    (`checkpoint-revert-driver.sh`, `configure-a-space-startup-script-runs.yaml`); an `evidence/`
    pointer becomes the measurement it carried. The four device names say what they are:
    `jd050-probe.sh`/`.txt` -> `acp-probe.sh`/`.txt` (beside the `claude-acp-probe.sh` fixture), and
    `p7e2-pip.txt`/`p7e2-jupyter.txt` -> `pip-version.txt`/`jupyter-version.txt`, each holding that
    script's version and rc. Both flows ran on the tablet with the new names.
    `.maestro/[a-c]*.yaml` is the glob.
  - [x] T8e2 — `.maestro/[d-m]*.yaml` (152 of the 690 flows, 107 hits in 64 of them, plus ids no gate
    pattern matches in about 23 more), now 0, 77 flows touched. The same rules as T8e1. The device
    names say what they are: `jd046-importtime-*` -> `acp-importtime-*` (not plain `importtime.txt`,
    which `git-panel-branch.yaml` already writes into the same workspace), `jd058-opencode-version.txt`
    -> `opencode-version.txt`, `p5b-*` -> `pylsp-*`, `p8d-alpha`/`-beta` -> `document-alpha`/`-beta`,
    and the kernel picker's `P8g Provider|Consumer|Emulator` -> `Kernel Picker …`, `p8g-echo` ->
    `echo-kernel`, `p8g_shared` -> `provider_shared`. `P8G_KERNEL` -> `PICKER_KERNEL` is a literal no
    gate pattern matches; it surfaced only in a device frame, so a rename the gate cannot see is
    checked by a case-insensitive `git grep` of the old stem and a look at the frame. `.maestro/[d-m]*.yaml`
    is the glob.
  - [x] T8f1 — `.maestro/[n-r]*.yaml` (159 flows, 90 hits in 47 of them, plus ids no gate pattern
    matches in 11 more), now 0, 58 flows touched. The whole `[n-z]` range was 225 hits in 115 flows,
    twice a T8e slice, so it split by letter. The same rules as T8e. The device names say what they
    are: `jd046-probe.*` -> `opencode-probe.*` (beside the fixture of that name, not `acp-probe`,
    which is the `claude-acp` probe), `jd058-console-accounts.sh`/`jd058-console.txt` ->
    `console-accounts.sh`/`console-accounts.txt`, `p5a-version.txt`/`p5a-init.out` ->
    `pylsp-stdio-*` (`pylsp-version.txt` is `language-server`'s), `no-such-package-jd003` ->
    `no-such-package`, and `p8f-second.ipynb` -> `running-panel-second.ipynb` with its fixture's
    title. The last three surfaced only in the broader `git grep`, so a rename the gate cannot see is
    found by that grep and checked on the device. `reinstall-demo-after.yaml` also carried an
    assertion that had been stale since `.chat` opened in the chat view; the first device run failed
    on it and it now asserts the message text the screen shows. `.maestro/[n-r]*.yaml` is the glob.
  - [x] T8f2 — `.maestro/s*.yaml` (71 flows, 61 hits in 30 of them, plus ids no gate pattern matches
    in a few more), now 0. The whole `[s-z]` range was 157 lines in 73 flows, so it split by letter:
    `[t-z]` is T8f3. The same rules as T8e. The device names say what they are: `p2j-5-share` ->
    `share-export` (the flow's own name), `p8b-install.log`/`p8b-pip.rc` -> `pip-install.*`, and the
    Space demo's `jd8-apk.*`, `jd8-jq.*`, `jd8-jq2.*` -> `apk-add.*`, `jq-run.*`, `jq-run-again.*`,
    with the two fixtures' cell ids. The `p2j-5-*` names in `IntentDocumentSharerTest` are Kotlin
    string literals and wait for T8g. `space-packages-driver.sh` fails at HEAD and after on the
    kernel cell (`No module named 'tabulate'`) while every leg that reads the renamed files passes:
    a defect of its own, not this task's. `.maestro/s*.yaml` is the glob.
  - [x] T8f3 — `.maestro/[t-z]*.yaml` (116 flows, 96 hits in 43 of them: `t` 74, `u` 3, `v` 2, `w` 17;
    the gate counts 84 problems in 40), now 0, which puts all 690 flows in its scope. The same rules
    as T8e. The device names say what they are, and a name shared by two flows carries its flow's
    prefix so none collides in a workspace: `p4j2-*` -> `ctrl-c-after.txt`/`ctrl-c-survived.txt`
    (`wkr-survived.txt` and `extra-keys-survived.txt` are the siblings'), `p4j3a*` -> `extra-keys-*`,
    `p4i-answer`/`p4i-listing` -> `shell-answer`/`shell-listing`, `p4k-*` -> `idle-marker-*` and
    `before-idle`/`after-idle`, `p4m-*` -> `verbatim-*`, `p4i-bg-*` -> `background-*`,
    `p4i-vi-*` -> `vi-*`, `p8e-renamed` -> `renamed-tab`. Three more no gate pattern matches (an
    underscore ends the `\b`) went with them: `p8e_marker_first`/`_second`/`p8e_dirty_probe` ->
    `tab_*` across the two fixtures, three flows and two drivers, the fixtures' `P8e First` titles,
    and the `p4i` in `terminal-marker.txt`. Four drivers fail at HEAD and after, identically, on
    something this task did not touch, each a defect of its own: `terminal-vi-driver.sh`'s top-strip
    oracle reads `False` while the frame shows `vi` drawn, `terminal-input-verbatim-driver.sh` expects
    a `sys.prefix` ending `/files/spaces/base/usr` and gets `/usr`, and `tabs-narrow` and `tabs-wide`
    stop at `Rename file` before the typed `renamed-tab`, so that rename ran on no device.
    `.maestro/[t-z]*.yaml` is the glob.
  - [ ] T8g — the 91 step-code-shaped string literals in 18 Kotlin files (`app/src/androidTest` device file
    names, kernelspec names). No gate reads a string literal, so the commit body names the `git grep` that
    measured it.
  - [ ] T8h — the globs collapse into `scripts` and `.maestro` in `SCANNED_TREES`; `.ipynb`, `.txt`, `.csv`,
    `.chat` and the extensionless `gradlew-jbr` get a scanner or join `NO_PROSE`; `AGENTS.md`'s tooling row
    and `docs/README.md` follow. Demo step 6 is run.
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
