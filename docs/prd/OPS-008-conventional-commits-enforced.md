# OPS-008 — Conventional commits are enforced

Milestone: `M10` · State: in-progress · Depends on: `OPS-006` (shipped) · Closes: —

## Outcome

A commit whose subject is not `type(scope): subject` is refused where it is written, and a pull request
whose commits or title are not conventional shows a red check on GitHub. `JD-066` can then cut a release
from the commit subjects without first auditing them.

## Demo

An OPS item (ADR-0122): no screen changes, so the demo is a run anyone can repeat, not a Maestro script.

1. `git commit --allow-empty -m "wip: x"` exits non-zero with commitlint's message; `feat(x): y` passes.
2. `npm test` (`node --test`) is green, and so is the same check under
   `pre-commit run commitlint-config-tests --all-files`.
3. A throwaway branch and pull request carry one bad commit and one bad title:
   `gh run list --workflow ci.yml --branch <it> --json conclusion` reports `failure`, and the failing job is
   `commit-messages`. After both are fixed the same query reports `success`. A gate that has only ever been
   seen green has not been shown to fail.
4. `pre-commit run actionlint-docker --all-files` and `pre-commit run zizmor --all-files` exit 0 over
   `.github/workflows/ci.yml` and `.github/dependabot.yml`.

The closing commit says the check is not yet *required*: a ruleset needs a public repository or GitHub Pro
(`gh api repos/dmenezesgabriel/jupydroid/rulesets` answers 403 today), so binding it belongs to the
visibility item, as it did for the code-scanning jobs
([ADR-0138](../adr/0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md)).

## Who else implements this

Read from the clones named in [`../README.md`](../README.md) by searching `.github/`, `package.json`,
`.pre-commit-config.yaml`, `CONTRIBUTING.md` and the repository root for `commitlint`,
`conventional-pre-commit`, `conventional commit`, `semantic-pull-request`, in the session that wrote this
PRD. An absence is evidence too.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e6906` | ABSENT | — | no enforcement of a commit format |
| `vscode@3f3380c6` | ABSENT | — | no enforcement of a commit format |
| `vscode-jupyter@f01d80ca` | ABSENT | — | no enforcement of a commit format |
| `intellij-community@406a27a8` | ABSENT | — | no enforcement of a commit format |
| `termux-app@3df69d1d` | ABSENT | — | no enforcement of a commit format |
| `winlator@fb66541b` | ABSENT | — | no enforcement of a commit format |
| `jupyterlab-git@b62a87b7` | ABSENT | — | no enforcement of a commit format |
| the shipped rootfs | ABSENT | — | not a repository |

**Count:** 0 of 8. None of the references is evidence for or against; the decision rests on the tools'
own documentation and on the maintainer's own repository.

`dmenezesgabriel/claude-acc-manager@5233d605` is authoritative (it is the maintainer's own). It enforces
the format with `compilerla/conventional-pre-commit` v4.4.0 at the `commit-msg` stage
(`.pre-commit-config.yaml`, "Conventional commits"), a Python repository with no Node. It has no
CI check of its own over a pull request's commits.

## Implementation inventory

### `commitlint` (`conventional-changelog/commitlint`, `@commitlint/cli` 21.2.4)

| Concern | Files |
|---|---|
| Rules | `@commitlint/config-conventional` (preset); `commitlint.config.mjs` overrides it |
| Local hook | `npm exec --no -- commitlint --edit <file>`, run by pre-commit at the `commit-msg` stage |
| CI range | `commitlint --from <base> --to <head>` |
| CI title | the title on stdin: `printf '%s' "$TITLE" \| commitlint` |
| Merge commits | `defaultIgnores`: `Merge pull request …`, `Merge branch …` are skipped |
| Engines | Node `>=22.12.0` |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Checker | `commitlint` (npm) · `conventional-pre-commit` (`claude-acc-manager`) · an own script like `scripts/check_markdown_links.py` | commitlint is the standard and parses body and footer, but brings Node, a lockfile and a CI setup into a Python and Gradle repository; the hook repo needs no Node but lives outside `uv.lock`; an own script needs no dependency but is one more parser to maintain | **commitlint**, the maintainer's decision. The cost is named here and in the ADR rather than discovered later |
| How the hook runs | `language: node` with `additional_dependencies` · `language: system` over a committed lockfile | `language: node` pins only the two named packages, so the transitive tree is unlocked, against [ADR-0123](../adr/0123-dependencies-are-locked-strict-and-verified-by-sha256.md); `language: system` needs `npm ci` once | `language: system`: `npm ci` fails on a sha512 mismatch, which is the same promise the Gradle and uv locks make |
| Types | the preset's eleven · the ten the history uses | `revert` is in the preset, and `AGENTS.md` forbids reverts | the preset minus `revert`: `feat fix docs test refactor build ci chore perf style` |
| Length and case | the preset's 100-column header, lowercase subject · none | 1603 subjects, p95 178 characters, max 313; capitalised subjects such as `refactor(tests): Kotlin fixtures …` exist | `header-max-length`, `body-max-line-length`, `footer-max-line-length`, `subject-case` and `footer-leading-blank` off |
| Where CI looks | every push · a pull request's own range | history holds 3 non-conventional non-merge subjects and is not rewritten ([ADR-0133](../adr/0133-history-is-rewritten-only-while-private-and-shared-with-nobody.md)) | pull requests only, `base.sha..head.sha`, plus the title |

Anything here that outlives the item becomes an ADR before the PRD is deleted.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The rule | `CONTRIBUTING.md` "Commits" asks for a conventional subject | `claude-acc-manager@5233d605 .pre-commit-config.yaml` | stated, not enforced: **absent** |
| Local gate | `.pre-commit-config.yaml` has `pre-commit` hooks only; `pre-commit install` installs that one type | same | no `commit-msg` hook: **absent** |
| CI | `.github/workflows/ci.yml` runs `python-gate` and `gradle-gate`; none reads a commit message | — | **absent** |
| Node toolchain | none: no `package.json`, no Node in CI | — | **absent** |
| Dependabot | `github-actions`, `uv`, `gradle` | — | no `npm` ecosystem |

## Deviations

None from commitlint's behaviour; the config states its overrides and the reason for each.

## Measurements

The commit history at the time of writing (`git log --format=%s`): 1603 subjects, 1541 with a scope, 47
without, p95 length 178, max 313; 12 merge subjects; 3 non-merge subjects that are not conventional
(`spike(rootfs): …` and two plain-prose subjects, `Add verification harness …` and `Scaffold JupyterDroid …`,
all from the first days). T1 re-ran it with the config
itself: `node_modules/.bin/commitlint --from 4c666d3f --to HEAD` (the first commit is outside the range)
reports exactly two failing subjects, `spike(rootfs): …` (`type-enum`) and `Add verification harness …`
(`type-empty`, `subject-empty`), and `Scaffold JupyterDroid …` is the excluded first commit. The preset's
`footer-leading-blank` warned on 170 bodies that close with a `Gates:` or `Reproduce:` line, which
commitlint parses as a footer; it is off, with a test, so the warning is not noise on one commit in ten.

## Decisions

- Checker is commitlint, run from a committed `package-lock.json` through `npm ci`, with the rules in the
  trade-offs table. The ADR is `ADR-0139`, written at T4.
- Host: Node 24.18.0 locally; CI pins the same version. commitlint 21.2.4 was published 2026-10-18, past
  Dependabot's 7-day cooldown.

## Surface

| Layer | Files |
|---|---|
| tool | `package.json`, `package-lock.json`, `commitlint.config.mjs`, `.gitignore` (`node_modules/`) |
| unit tests | `commitlint.config.test.mjs`, run by `node --test` |
| hooks | `.pre-commit-config.yaml` (`default_install_hook_types`, `commitlint`, `commitlint-config-tests`) |
| CI | `.github/workflows/ci.yml` (`commit-messages`), `.github/dependabot.yml` (`npm`) |
| docs | `CONTRIBUTING.md`, `AGENTS.md` tooling table, `docs/adr/0139-*.md`, `docs/backlog.md` |

## Tasks

- [x] T1 — `package.json`, `package-lock.json`, `commitlint.config.mjs` and `commitlint.config.test.mjs`
  (red first), `node_modules/` in `.gitignore`; re-measure the history with the config and record it above
- [ ] T2 — the `commit-msg` hook and the config-test hook in `.pre-commit-config.yaml`,
  `default_install_hook_types`, and the install steps in `CONTRIBUTING.md`
- [ ] T3 — the `commit-messages` job in `ci.yml` (range and title), `python-gate`'s `SKIP` list, and the
  Dependabot `npm` block
- [ ] T4 — close: Demo steps 1–4, `ADR-0139`, the `AGENTS.md` tooling row, the `CONTRIBUTING.md` Commits
  section, delete this PRD, edit `docs/backlog.md`

## Out of scope

- Requiring the check (a ruleset): needs a public repository or Pro; owned by the visibility item.
- Cutting a release from the commits: `JD-066`.
- Rewriting the 3 old subjects: [ADR-0133](../adr/0133-history-is-rewritten-only-while-private-and-shared-with-nobody.md).
