# ADR-0137 — Dependabot updates Actions and `uv`, and only secures Gradle

Status: accepted
Date: 2026-11-05

## Context

CI runs the commit gate on every push and pull request (`.github/workflows/ci.yml`), with every action
pinned by SHA. A pin by SHA only stays sustainable if something moves it, and
[ADR-0123](0123-dependencies-are-locked-strict-and-verified-by-sha256.md) locks Gradle strictly with
`gradle/verification-metadata.xml`, which a tool that does not run Gradle cannot rewrite.

Measured on 2026-11-05, the first day `.github/dependabot.yml` was on `main`, with all three ecosystems
listed (`github-actions`, `uv`, `gradle` over five directories) and a 7-day cooldown on each:

- `uv` opened one pull request (`ruff` 0.15.22 → 0.17.3, `pyproject.toml` and `uv.lock`). `python-gate` and
  `gradle-gate` both passed.
- `gradle` opened five (`compose-bom`, `okhttp`, `commons-lang3`, `bcutil-jdk18on`, `zstd-jni`). **All five
  failed `gradle-gate`**, in two ways. Three changed `gradle/libs.versions.toml` and some `gradle.lockfile`s,
  and Android Lint stopped with "Dependency verification failed" because the new artifacts have no checksum.
  Two changed `build.gradle.kts` or the catalog, and ktlint stopped with "Could not resolve all artifacts for
  configuration 'classpath'", for example "Did not resolve 'org.bouncycastle:bcprov-jdk18on:1.85.2' which
  is part of the dependency lock state", because `buildscript-gradle.lockfile` was not touched.
- `github-actions` opened none: `actions/checkout` and `astral-sh/setup-uv` were at their latest release, and
  `actions/setup-java` v6.2.0 was published a day earlier, inside the cooldown.

The tools/rootfs `.lock` files are not listed on purpose. They are `uv pip compile --python-platform
aarch64-unknown-linux-musl --generate-hashes` output, and Dependabot's pip ecosystem drives pip-compile
([ADR-0130](0130-python-dependencies-are-locked-and-verified-by-sha256.md)).

The option's behaviour is from GitHub's
[Dependabot options reference](https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference):
"You can temporarily disable version updates for a package manager by setting this option to zero", and
"Security update pull requests are subject to a separate, fixed limit of 10 open pull requests".

## Decision drivers

- A pull request that is red by construction teaches the reader to ignore red.
- A bump of a locked dependency is the documented procedure in `AGENTS.md` either way, so a bot that cannot
  perform it adds nothing but a queue.
- A vulnerable dependency should still reach the maintainer.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep weekly Gradle version updates | an always-current list of what is outdated | five red pull requests on day one, each needing the manual procedure first |
| Remove the `gradle` entries | simplest file | drops the vulnerability signal |
| Gradle on `open-pull-requests-limit: 0` | no red version pull requests; security updates still open | a security pull request is also red until the manual procedure runs on it |

## Decision

`dependabot.yml` keeps `github-actions` and `uv` as weekly version updates and sets
`open-pull-requests-limit: 0` on `gradle`, so Gradle gets security updates only.

## Consequences

A Gradle bump stays the manual procedure in `AGENTS.md` (write the locks and the verification metadata
together, from an empty Gradle home). A Gradle security pull request will still fail `gradle-gate` until
someone runs it; the failure is the prompt, not a defect to fix in the workflow.

A reader who sees `open-pull-requests-limit: 0` will be tempted to delete the entry as dead. It is not
dead: it is what keeps security updates for Gradle. Turning version updates back on needs a way to regenerate
`gradle/verification-metadata.xml` and `buildscript-gradle.lockfile` on the pull request's branch first.

**CI cost, measured on the first push to `main` (2026-11-05, 2 vCPU `ubuntu-24.04`).** `python-gate` took 2m13s
and `gradle-gate` 17m51s (Android Lint 8m19s, `assembleDebug` 3m11s, ktlint 2m37s, unit tests 2m13s, detekt
36s). Billed per job rounded up, a run is 3 + 18 = 21 minutes, so the 2,000 free Linux minutes a month carry
about 95 runs while the repository is private. An earlier pull-request run, on a cold Gradle cache, measured
15m58s for `gradle-gate`; the run on `main` was slower, so the Gradle cache has not yet shortened a run.
