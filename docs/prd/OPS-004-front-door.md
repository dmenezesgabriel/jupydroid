# OPS-004 — A front door for strangers

Milestone: `M9` · State: in-progress · Depends on: `OPS-003` · Closes: —

## Outcome

A stranger who lands on the repository learns what Jupydroid is, how to build and install it, how to
contribute and how to report a vulnerability, from the files GitHub's community profile looks for.

## Demo

An OPS item (ADR-0122): no screen changes, so the demo is a run anyone can repeat, not a Maestro script.

1. In a fresh clone, `uv run --locked python scripts/check_markdown_links.py $(git ls-files '*.md')`
   exits 0: every relative link in the front-door files resolves to a tracked file.
2. `gh api repos/dmenezesgabriel/jupydroid/community/profile` lists a README, `CONTRIBUTING`,
   `SECURITY`, a code of conduct, issue templates and a pull-request template.
3. Every frame under `docs/images/` passes `scripts/check_device_secrets.py` and was looked at: no
   status bar, no notification, no Wi-Fi name.
4. The README's build section, followed on a host with Docker, the Android SDK and a JDK, produces an
   APK that `adb install` accepts on an arm64 device.

Step 4 is the one only the maintainer's hardware can run; the commit body that closes the item names it.

## Who else implements this

Read from the clones named in [`../README.md`](../README.md), in the session that wrote this PRD. An
absence is evidence too.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e6906` | PARTIAL | `README.md`, `CONTRIBUTING.md`, `.github/pull_request_template.md` | No `SECURITY.md`, code of conduct or issue template in the clone; the PR template asks for references, code changes, user-facing changes and an "AI usage" yes/no |
| `vscode@3f3380c6` | PARTIAL | `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `.github/ISSUE_TEMPLATE/`, `.github/pull_request_template.md` | Everything but a code of conduct in the clone |
| `vscode-jupyter@f01d80ca` | FULL | `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE.md` | The only complete set; its code of conduct is six lines adopting Microsoft's by link, and its `config.yml` also turns blank issues off |
| `intellij-community@406a27a8` | PARTIAL | `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md` | No templates; its code of conduct is one paragraph linking JetBrains' |
| `termux-app@3df69d1d` | PARTIAL | `README.md`, `SECURITY.md`, `.github/ISSUE_TEMPLATE/01-bug-report.yml`, `.../02-feature-request.yml`, `.../config.yml` | The closest Android analogue: issue forms with `blank_issues_enabled: false`, a one-line `SECURITY.md` pointing at a web page, no `CONTRIBUTING.md` in the clone |
| `winlator@fb66541b` | PARTIAL | `README.md`, `.github/ISSUE_TEMPLATE/bug_report.md` | One Markdown bug template asking for app version and device model |
| the shipped rootfs | ABSENT | — | The rootfs is not a repository |

**Count:** 6 of 6 repositories have a README; 4 have a `CONTRIBUTING.md`, 4 a `SECURITY.md`, 4 issue
templates and 3 a pull-request template; only 2 have a code of conduct, and both adopt their
organisation's by link. Only `vscode-jupyter` has all six. A README is universal, and the rest is
common enough that a stranger expects it. A code of conduct is rare and always borrowed, which
settles *what* to write (a recognised text, not our own) and not *whether*: this repository has no
organisation to link to, so it carries the text.

Which reference answers which question is in
[`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) §8.3. None of these seven is
authoritative for repository hygiene, so each row above is "what that project does", not "what we copy".

## Implementation inventory

### `vscode-jupyter@f01d80ca`

| Concern | Files |
|---|---|
| Contributor setup, build, test, pre-commit | `CONTRIBUTING.md` (sections Prerequisites, Setup, Running Unit Tests) |
| Issue forms | `.github/ISSUE_TEMPLATE/2_bug_form.yml`, `.github/ISSUE_TEMPLATE/config.yml` |
| Pull-request template | `.github/PULL_REQUEST_TEMPLATE.md` |
| Code of conduct | `CODE_OF_CONDUCT.md` |

### `termux-app@3df69d1d`

| Concern | Files |
|---|---|
| Issue forms that route a package bug elsewhere | `.github/ISSUE_TEMPLATE/01-bug-report.yml` |
| Blank issues off, questions to Discussions | `.github/ISSUE_TEMPLATE/config.yml` |
| Security policy | `SECURITY.md` |

### `jupyterlab@7f0e6906`

| Concern | Files |
|---|---|
| Pull-request template with an AI-usage disclosure | `.github/pull_request_template.md` |

Where they disagree: `vscode` and `vscode-jupyter` send security reports to Microsoft's response
centre (a web portal, and for `vscode-jupyter` an address too), `intellij-community` to a restricted
tracker issue type or a PGP-encrypted address, and `termux-app` to a web page. Every one of them relies
on an organisation's reporting channel. None points at GitHub's private vulnerability reporting, which
is the channel a repository with no organisation behind it has, so this item decides it (below).

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Code of conduct text | `vscode-jupyter`, `intellij-community` → link to the organisation's | We have no organisation | The Contributor Covenant 2.1, in full, with its CC BY 4.0 attribution; a placeholder for the contact until the maintainer supplies one |
| Security reporting | `termux-app` → a web page; `vscode-jupyter`, `intellij-community` → an organisation's portal or address | An address is personal data (ADR-0133); a page needs hosting; a portal needs an organisation | GitHub private vulnerability reporting; it exists only on a public repository, so `OPS-005` turns it on |
| Blank issues | `termux-app` → off, forms only | Forms get reproducible reports; they also turn away a drive-by | Off, with questions routed to Discussions |
| Contribution bar | `jupyterlab`, `vscode-jupyter` → a setup guide and a PR template | Ours also demands a device flow | The full working agreement (`AGENTS.md`), the device rule stated as "an arm64 Android device", and the flow's result in the PR body |
| Screenshots | every README above shows none or hosts them elsewhere | A committed image is a published image | Two or three frames under `docs/images/`, cropped past the status bar, each looked at |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| README | `README.md`: present and partial; it says "eleven measured gaps" where `docs/architecture.md` holds 154 and lists the Space system as unbuilt though it shipped | `vscode-jupyter@f01d80ca README.md` | stale claims, no install section, a build section that stops at `assembleDebug` |
| Contributing | absent; `AGENTS.md` carries the agreement but addresses agents, not people | `vscode-jupyter@f01d80ca CONTRIBUTING.md` | absent |
| Security policy | absent | `termux-app@3df69d1d SECURITY.md` | absent |
| Code of conduct | absent | `vscode-jupyter@f01d80ca CODE_OF_CONDUCT.md` | absent |
| Issue and PR templates | absent; there is no `.github/` | `termux-app@3df69d1d .github/ISSUE_TEMPLATE/`, `jupyterlab@7f0e6906 .github/pull_request_template.md` | absent |
| Committed images | none outside fixtures and launcher icons | — | no home, no crop step |
| Link check | none; `scripts/check_evidence_citations.py` and `scripts/check_retired_doc_citations.py` look for specific names only | — | absent |

## Deviations

- No `CODEOWNERS`, labeler, Dependabot or CI file: those belong to M10.
- No `FUNDING.yml` (`termux-app`, `winlator`): nothing here asks for money.
- The code of conduct is carried in full rather than linked, because there is no organisation to link to.

## Open decisions

- **Contact for conduct reports.** Settled for now: a marked placeholder in `CODE_OF_CONDUCT.md`. The
  real channel is added later, and `OPS-005` lists filling it as a precondition to going public.
- **An AI-usage line in the PR template** (`jupyterlab@7f0e6906 .github/pull_request_template.md`).
  This repository is built with agents, so the line is honest, but it is a policy, not a template
  detail. Decided in T5 with evidence of what the line asks for there.
- **`SECURITY.md` before the feature exists.** Private vulnerability reporting is off on a private
  repository. T2 writes the policy; `OPS-005` turns the feature on. Until then the file names the
  route it will have.

## Surface

| Layer | Files |
|---|---|
| repository root | `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md` |
| `.github/` | `ISSUE_TEMPLATE/` (two forms, `config.yml`), `pull_request_template.md` |
| `docs/images/` | two or three cropped frames |
| `scripts/` | `check_markdown_links.py`, a crop function for the frames, a tablet driver for them, each with a `_test.py` |
| `.pre-commit-config.yaml` | one hook for the link gate |

No `domain/`, `data/`, `ui/` or `di/` file changes, so there is no port and no `Fake…` to add.

## Tasks

- [ ] T1 — a Markdown link gate: `scripts/check_markdown_links.py` with `_test.py`; every relative link
  in a tracked Markdown file resolves to a tracked file; wired into `.pre-commit-config.yaml`
- [ ] T2 — `SECURITY.md`: private vulnerability reporting; scope is the loopback server's token, the MCP
  port and the proot sandbox; no released versions yet
- [ ] T3 — `CODE_OF_CONDUCT.md`: the Contributor Covenant 2.1 with its attribution and a contact placeholder
- [ ] T4 — `CONTRIBUTING.md`: the working agreement for people, `pre-commit install`, the gate command,
  ids are ephemeral, evidence is local, the device rule stated generically
- [ ] T5 — `.github/` issue forms, `config.yml` (blank issues off, security routed to advisories) and a
  pull-request template whose checklist mirrors the commit-body gates
- [ ] T6 — README screenshots: a tested crop function and a tablet driver; frames under `docs/images/`,
  each looked at and passed through `scripts/check_device_secrets.py`
- [ ] T7 — README rewrite: what it is, status as a produced count rather than a stored one, install
  (no release exists until the delivery milestone, so build and `adb install`), build from source
  including the rootfs
- [ ] T8 — close: run the community-profile command, write an ADR only if a decision outlives the item,
  delete this PRD, edit the backlog row, add the contact precondition to `OPS-005`

## Out of scope

Making the repository public, secret scanning, branch protection (`OPS-005`); CI, Dependabot and the
release pipeline (M10); the documentation site; the code of conduct's real contact, added after this
item and tracked on `OPS-005`.
