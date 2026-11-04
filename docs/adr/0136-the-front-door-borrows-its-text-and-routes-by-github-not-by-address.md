# ADR-0136 — The front door carries a recognised conduct text and routes reports through GitHub, not an address

Status: accepted
Date: 2026-11-04

## Context

A stranger expects a README, `CONTRIBUTING.md`, `SECURITY.md`, a code of conduct and issue and pull-request
templates. Of the six reference repositories, all have a README, four a `CONTRIBUTING.md`, four a
`SECURITY.md`, four issue templates and three a pull-request template. Only two have a code of conduct, and
both adopt their organisation's by link (`vscode-jupyter@f01d80ca`, `intellij-community@406a27a8`). Every
one sends security reports through an organisation's portal or address, or a web page
(`termux-app@3df69d1d`). This repository has no organisation, and an address is personal data
([ADR-0133](0133-history-is-rewritten-only-while-private-and-shared-with-nobody.md)).

## Decision

- **Code of conduct:** the Contributor Covenant 2.1 in full, with its CC BY 4.0 attribution. There is no
  organisation to link to. The report contact is a marked placeholder in `CODE_OF_CONDUCT.md` until the
  maintainer supplies one.
- **Security reports:** GitHub private vulnerability reporting, named in `SECURITY.md`. The file publishes no
  address. The feature exists only on a public repository, so a report cannot be filed while this one is
  private.
- **Issues:** forms only, `blank_issues_enabled: false` (`termux-app@3df69d1d`), questions routed to
  Discussions and vulnerabilities to advisories.
- **Pull requests:** the template's checklist mirrors the commit-body gates, and it asks whether AI
  generated the change (`jupyterlab@7f0e6906 .github/pull_request_template.md`). This repository is built
  with agents, so the line is honest.
- **Screenshots:** a committed image is a published image. Frames under `docs/images/` are cropped past the
  status bar by `scripts/crop_system_bars.py` and pass `scripts/check_device_secrets.py`.
- **Links:** `scripts/check_markdown_links.py` fails a relative link that does not resolve to a tracked file.

## Consequences

Until the repository is public, the security route in `SECURITY.md` and the conduct contact are not
usable. Both are preconditions of making it public. GitHub's community profile reports
`issue_template: null` for the two YAML forms even though they sit under `.github/ISSUE_TEMPLATE/` on the
default branch; its `health_percentage` is 100.
