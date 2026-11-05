# ADR-0139 — Commit subjects are checked by commitlint from a locked npm tree and written with commitizen

Status: accepted
Date: 2026-11-05

## Context

`CONTRIBUTING.md` asked for a conventional-commit subject and nothing enforced it. A release cut from the
subjects (the delivery item after this one) needs them to be one shape, and auditing 1,600 of them by hand
first is the work a gate exists to avoid.

None of the 8 references at the pins in `docs/README.md` enforces a commit format, so they are no evidence
either way. The maintainer's own `dmenezesgabriel/claude-acc-manager@5233d605` runs
`compilerla/conventional-pre-commit` at the `commit-msg` stage, and has no check over a pull request.

Measured on 2026-11-05 over `git log --format=%s`: 1,603 subjects, 12 merges, 3 non-merge subjects that are not
conventional (`spike(rootfs): …` and two plain-prose ones from the first days), a p95 length of 178 characters and
a maximum of 313, capitalised subjects such as `refactor(tests): Kotlin fixtures …`, and about one body in ten
closing with a `Gates:` or `Reproduce:` line that commitlint parses as a footer.

## Decision drivers

- Refuse a bad subject where it is written, and show a red check where a contributor never installed the hook.
- The rules the prompt asks about and the rules the hook enforces must be one list, or the prompt offers what the
  hook refuses.
- A dependency tree that runs on a developer's machine and in CI is a supply-chain surface, whatever it is for
  ([ADR-0123](0123-dependencies-are-locked-strict-and-verified-by-sha256.md)).
- `AGENTS.md` forbids reverts and rewriting history; a rule must not require either.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `conventional-pre-commit` (the maintainer's other repository) | a Python hook, no Node | its own rules, no check over a pull request's range or title |
| An own script like `scripts/check_markdown_links.py` | no dependency | one more parser to maintain |
| **commitlint** | the standard; parses body and footer; one CLI for a message, a range and a title | brings Node and a lockfile into a Python and Gradle repository |
| Writer: `cz-conventional-changelog` | the classic adapter | its own type list, which includes `revert` |
| Writer: Python `commitizen` | fits `uv.lock` | its rules live in `pyproject.toml`, a second copy; `cz bump` overlaps the release item |
| **Writer: commitizen with `@commitlint/cz-commitlint`** | the prompt reads `commitlint.config.mjs` | 145 more packages |
| Hook: `language: node` with `additional_dependencies` | no `npm ci` | pins only the named packages, leaving the transitive tree unlocked |
| **Hook: `language: system` over `package-lock.json`** | `npm ci` fails on a sha512 mismatch | a clone needs `npm ci` once |

## Decision

**commitlint, written with commitizen, from a committed `package-lock.json`.**

- `commitlint.config.mjs` extends `@commitlint/config-conventional` and states six overrides. The types are
  `feat fix docs test refactor build ci chore perf style`, without the preset's `revert`. The header, body and
  footer length caps, `subject-case` and `footer-leading-blank` are off, because the history holds what each would
  refuse.
- `.pre-commit-config.yaml` installs both hook types (`default_install_hook_types`) and sets
  `default_stages: [pre-commit]`: a hook with no `stages` runs at every installed stage, so without it the whole
  gate, Gradle included, ran a second time at `commit-msg`. The `commitlint` hook runs `npm exec --no -- commitlint
  --edit`; `commitlint-config-tests` runs `npm test` when a root-level `.mjs`, `.npmrc` or the lockfile changes.
- `npm run commit` starts commitizen with `@commitlint/cz-commitlint`.
- The `commit-messages` job in `ci.yml` runs on pull requests only and checks `base.sha..head.sha` and the title.
  A push to `main` is no range anyone can ask to be clean, and history is not rewritten
  ([ADR-0133](0133-history-is-rewritten-only-while-private-and-shared-with-nobody.md)).
- `.npmrc` sets `ignore-scripts=true`, `allow-git=none`, `min-release-age=7` (the Dependabot cooldown),
  `engine-strict=true` and `save-exact=true`. `osv-scanner` reads `package-lock.json`, `npm audit signatures` checks
  registry signatures and provenance, and Dependabot has an `npm` block with the same cooldown.

## Consequences

**Not required yet.** The check shows a red mark on a pull request and blocks nothing: a ruleset needs a public
repository or GitHub Pro, and `gh api repos/dmenezesgabriel/jupydroid/rulesets` answers 403. Binding
`commit-messages` belongs to the item that flips the visibility, as it did for the code-scanning jobs
([ADR-0138](0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md)). A required
check binds to the job's `name:`, so renaming `commit-messages` means updating the ruleset in the same commit.

**Shown to fail, then pass.** A throwaway pull request carrying `wip: x` and the title `Bump x from 1 to 2` failed
`commit-messages` at the range step with `type must be one of […] [type-enum]` and at the title step with
`subject may not be empty` and `type may not be empty`, and passed once both were fixed. The first run skipped the
title step after the range step failed; it now runs whatever happened before it. Locally, `git commit --allow-empty
-m "wip: x"` exits 1 and makes no commit.

**A title fix needs a push.** `pull_request` runs on `opened`, `synchronize` and `reopened`, not `edited`, so
correcting only the title does not re-run the job; push a commit or re-run it. Adding `edited` would re-run all of
`ci.yml`, Gradle included.

**The npm tree is 216 packages, up from 71.** Every one has a sha512 `integrity`, resolves on
`registry.npmjs.org` and has no install script, so `ignore-scripts` costs nothing. Two are deprecated, `glob@7.2.3`
and `inflight@1.0.6`, both under `commitizen`; `npm audit` reports 0 vulnerabilities and they are the price of the
writer. `npm audit signatures --min-release-age=0` verifies all 216 registry signatures and 18 provenance
attestations. `osv-scanner` read `package-lock.json` on a runner and counted 199 packages; that is fewer than 216,
probably because it counts each name and version once.

**The cooldown is enforced where a version is chosen.** `min-release-age=7` governs `npm install`, and Dependabot
holds its own 7 days; `npm ci` installs what the lock records. `es-toolkit@1.53.1`, which commitlint requires, was
published inside that window before `.npmrc` existed, so `npm audit signatures` refuses it under the cooldown. CI
lifts the cooldown for that one command, since verifying a signature does not choose a version.

**Dependabot's prefix is inferred.** It writes `build(deps): …` and `build(deps-dev): …` here from the history, and
a body with a YAML block and a `Signed-off-by` footer passes the rules. No `github-actions` pull request has been
seen yet; a wrong prefix would show as a red `commit-messages`, not pass quietly.

**Node joins the contributor tools.** Node 22.12 or newer and one `npm ci` after `pre-commit install`; a clone
that skipped it has its commits refused by the hook.
