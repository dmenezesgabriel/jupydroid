# OPS-019 — `docs` joins the required checks once it can

Milestone: `M9` · State: open · Depends on: OPS-020 shipped (`docs.yml` on `main`, first Pages
`deploy` green) · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Every pull request into `main` is gated by the `docs` check — the strict MkDocs build — and a
subsequent Pages `deploy` run is watched green, so the check that guards the site is itself proven
not to break the pipeline it guards.

## Demo

An OPS item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): no screen
changes, so the demo is a run anyone can repeat, not a Maestro script.

1. `gh api repos/dmenezesgabriel/jupydroid/rulesets/27844457 --jq
   '[.rules[].parameters.required_status_checks[].context]'` lists nine contexts, `docs` among them —
   it listed eight before T2.
2. `gh api "repos/dmenezesgabriel/jupydroid/commits/<pr-head>/check-runs?per_page=50" --jq
   '[.check_runs[].name] | index("docs")'` answers a number for **every** open pull request — the
   three whose heads predate `docs.yml` on `main` (PRs 22, 32, 34 at writing) were nudged in T2, so
   none sits at "Expected — waiting for status" forever.
3. `gh run list --workflow=docs.yml --branch=main` shows the `deploy` job `success` on the ship
   merge's push — the *subsequent* deploy OPS-020's out-of-scope handed here — and
   `curl -fsS -o /dev/null -w "%{http_code}" https://dmenezesgabriel.github.io/jupydroid/` answers
   `200`.

## Who else implements this

Repository settings are hosted state, not tree content: every reference reads ABSENT for want of
evidence, as OPS-005 measured. The shape being copied is the sibling repository's ruleset, already
adopted — and the sibling's own ruleset already names `docs` among its required contexts, which is
the precedent this item completes on our side.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | — | settings are hosted state, not in the tree |
| `vscode@3f3380c668d0` | ABSENT | — | same |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | — | same |
| `intellij-community@406a27a8da07` | ABSENT | — | same |
| `termux-app@3df69d1da197` | ABSENT | — | same |
| `winlator@fb66541b93a4` | ABSENT | — | same |
| the shipped rootfs | ABSENT | — | not a repository with settings |
| `claude-acc-manager@5233d605` | PARTIAL | ruleset `24735780` via `gh api` | its `required_status_checks` already names `docs` — the shape this item brings our ruleset to |

**Count:** 0 of 7 references can show the settings; the sibling supplies the shape only. The decision
is measured read-backs against our own ruleset and workflow, not a reference.

## Implementation inventory

### GitHub REST API (docs.github.com, verified against the live repository 2026-11-22)

| Concern | Endpoint |
|---|---|
| Read the ruleset | `GET /repos/{owner}/{repo}/rulesets/27844457` |
| Add `docs` to it | `PUT /repos/{owner}/{repo}/rulesets/27844457` — the only write endpoint; the body is the **whole** ruleset (`name`, `target`, `enforcement`, `conditions`, `rules`, `bypass_actors`), so the eight existing contexts and both policy flags go back verbatim with `docs` appended |

### Our own files and state the tasks touch

| Concern | Files / state |
|---|---|
| The check being required | `.github/workflows/docs.yml` — job `docs` (`name: docs`, `mkdocs build --strict -f docs-site/mkdocs.yml`) on `pull_request`, `push: [main]`, `workflow_dispatch`; job `deploy` is `push`-only and `visibility == 'public'`-guarded; both `name:` values are the required-check API surface (the file's own header says renaming one means updating the ruleset in the same commit) |
| The ruleset | `main required checks`, id `27844457`, `active`, `~DEFAULT_BRANCH`, one `required_status_checks` rule: `commit-messages`, `python-gate`, `gradle-gate`, `gitleaks`, `osv-scanner`, `opengrep`, `codeql (actions)`, `codeql (python)` — `docs` absent |
| The precondition, already delivered | `docs.yml` reached `main` in `058009a8a` (OPS-009 T2); the first `deploy` ran green on the OPS-020 merge push (Docs run `45038107056`, deploy job `137256385648`) and again on `45050828644`; `https://dmenezesgabriel.github.io/jupydroid/` answers `200` (measured 2026-11-22) |
| The deadlock the row names | open PRs `22`, `32`, `34` (dependabot) — heads **lack** `.github/workflows/docs.yml` (contents API 404) and carry **no** `docs` check run; last `pull_request` activity predates `058009a8a`; all three `MERGEABLE`. PRs `38`–`42`, `47` already report `docs` = `success` |
| The subsequent deploy | the ship merge's own push to `main` fires `docs.yml` — the run T3 watches |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the ruleset is edited | GitHub UI; REST API | the UI leaves no reproducible record; the API's read-back is itself the evidence | REST API via `gh api`, every change verified by a `GET` in the same task's commit body (the OPS-005 precedent) |
| Which contexts join | `docs` alone; `docs` and `deploy` | `deploy` never runs on `pull_request` — it is `push`-only — so requiring it would mean nothing or deadlock; OPS-005's PRD already ruled the deploy jobs are not merge gates | `docs` alone — it is the job that runs on `pull_request` and must pass |
| When the PUT lands | before this item's PRs merge; after | before, the ship PR itself is gated by `docs` — the new requirement proves itself on the very merge that records it; after, the first gated merge is someone else's | before: T2 applies the ruleset while this item's PRs are still open |
| Stale PRs 22/32/34 | `gh pr close` + `gh pr reopen`; `@dependabot rebase`; leave them | `pull_request` workflows run on the merge commit (`refs/pull/N/merge`), which contains `docs.yml` from `main`, and `reopened` is a default activity type — close/reopen should surface `docs` without touching the branches; rebase rewrites dependabot's branches and waits on dependabot; leaving them shows "Expected" until their next sync | close/reopen first, verify `docs` reports on each; `@dependabot rebase` is the recorded fallback for any that does not |
| Backlog end-state | keep `← next` on a shipped row; remove it | OPS-019 is the last open row — 127 shipped, this one `open`; nothing follows to mark | the ship commit sets the row `shipped` and drops `← next`; the next session's `grep -n "← next"` finds nothing, which is the correct signal that the ledger is empty |

## Gap analysis — what we already have vs what the item needs

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| `docs.yml` on `main` | present since `058009a8a` | — | **present** — the precondition OPS-020's T2 delivered |
| First Pages `deploy` | green twice (`45038107056`, `45050828644`); site answers `200` | — | **present** — OPS-020 proved the first; this item owes a *subsequent* one, which the ship merge produces |
| `docs` in the ruleset | absent — eight contexts | sibling ruleset already names `docs` | absent — one `PUT` away |
| Every open PR reporting `docs` | six of nine do; PRs 22/32/34 predate `docs.yml` on `main` | — | partial — T2's nudges close it |

## Deviations

- No `domain`/`data`/`ui`/`di` surface: this item ships no port and no app capability, so there is no
  Maestro flow ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)).
- The ruleset is GitHub state, not a file, so T2–T3's commits carry the PRD's checkbox tick, the API
  read-backs, the run ids and the curl result in the body — there is nothing else to change
  (the OPS-005 precedent).
- `docs/prd/` and `docs/backlog.md` are outside the MkDocs `docs_dir` (`docs-site/pages/`), so this
  item's own edits cannot break the `docs` check it is about to require.

## Open decisions

| Decision | Evidence to gather | Task |
|---|---|---|
| Whether close/reopen surfaces `docs` on PRs whose heads lack `docs.yml` | the `check-runs` listing on PR 34's head after `gh pr reopen` — `pull_request` runs on the merge commit, which carries `docs.yml` from `main`; if the listing stays empty, `@dependabot rebase` puts the file in the head tree instead | T2 tries close/reopen on all three, verifies each, falls back per-PR |

## Surface

| Layer | Files |
|---|---|
| repo settings | GitHub API only — no file (`PUT`/`GET /repos/{owner}/{repo}/rulesets/27844457`) |
| workflow | `.github/workflows/docs.yml` — read, not edited |
| GitHub state | ruleset `27844457`; open PRs `22`, `32`, `34` (nudged), `38`–`42`, `47`; the Pages deployment |
| docs | this PRD (deleted at ship); `docs/backlog.md` row (T3) |

## Tasks

- [x] T1 — This PRD.
- [x] T2 — Ruleset gains `docs`: `GET` ruleset `27844457`, `PUT` it back with the same eight contexts
  plus `{context: "docs"}` — `strict_required_status_checks_policy: false` and
  `do_not_enforce_on_create: false` preserved verbatim; `GET` read-back naming nine contexts. Then
  `gh pr close` + `gh pr reopen` on PRs `22`, `32`, `34` so their merge commits pick up `docs.yml`,
  and verify `docs` appears in each head's check-runs (`@dependabot rebase` for any that does not).
  The commit body quotes the `PUT`, the read-back, and the per-PR check-run evidence.
- [ ] T3 — Ship: delete this PRD; `docs/backlog.md` row to `shipped`; `← next` removed (no open
  items remain). The ship merge's push fires `docs.yml` — `gh run watch` the `deploy` job to green
  and `curl -fsS -o /dev/null -w "%{http_code}" https://dmenezesgabriel.github.io/jupydroid/` to
  `200`. The commit body names the run and job ids, the check-run ids it gated on, the curl result,
  the gate results, and says `Demo run by the agent, not by a human`.

## Out of scope

Requiring `deploy` or any other push-only job — it is not a merge gate (OPS-005's decision stands).
Merging, closing or rebasing the dependabot pull requests' content — T2 only makes them *report*
`docs`; their fate is the maintainer's. Repairing unrelated red checks on open pull requests — a red
required check is a defect to name, not to fix here. Editing `docs.yml` — its `name:` values are the
contract and nothing in this item moves them.
