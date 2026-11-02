# ADR-0132 — Keep device evidence local, and ship reproduction as a tested script

Status: accepted
Date: 2026-11-02

## Context

`AGENTS.md` rule 2 asked for every device flow's screenshots, snapshots and logs in `evidence/<step>/`,
committed. By the time the repository was being prepared to go public, that was 7,388 tracked files and
52 MB in the tip, 2.3 GB on disk with the ignored dumps beside them.

The directory caused three problems:

- **It carried the host and the house.** [ADR-0015](0015-rewrite-history-once-to-take-the-device-out-of-it.md)
  rewrote history once to take full-device dumps out of it. A later sweep found the host's home path in 462
  commits whose diff added or removed it, and in 455 of them it was only under `evidence/`. Every carrier of a
  Jupyter server token was under `evidence/` as well. Each new run is another chance to commit something
  nobody read.
- **It was what step codes existed for.** A driver, its flow and its evidence directory were named in
  lockstep, so a step code was the index that tied them together, and the doc-citations gate needed an
  exemption for drivers and flows to allow it.
- **It is not how open-source projects record a result.** A stranger can neither rerun a screenshot
  nor review one in a diff. A result can be checked by rerunning the command that produced it.

Tests that read real frames show the difference. Three `scripts/*_test.py` classes asserted a
defect off frames under `evidence/`. In a clone without that directory they were the only tests that
failed, 9 of 597.

## Decision drivers

- A fresh clone holds nothing about the host, the device or the house it ran in.
- Every tracked result reproduces from a fresh clone.
- Verification on the device keeps its full weight. Only where its output is kept changes.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep committing evidence, gated harder | Nothing to change | Every run is another publication of what the gates did not anticipate |
| Commit a curated subset (READMEs, small PNGs) | Some of the record stays browsable | A second policy to police, and still not reproducible |
| Keep evidence local; ship reproduction as tested scripts | Nothing device-side reaches the remote; what is tracked reruns | The screenshots behind an old decision exist only on the disk that ran them |

## Decision

**`evidence/` is local and gitignored. A result that must be reproducible ships as a tested script.**

- Drivers still write screenshots, hierarchy dumps, `maestro.log` and `logcat` to `evidence/<step>/`, and
  the agent still looks at the screenshots (`AGENTS.md` rule 2). They are not committed.
- A step closes on the commit body: the gate results and the command that reproduces what was measured.
  A measurement worth rerunning becomes a script under `scripts/` with a `_test.py` and a docstring that
  says how to run it.
- A test never reads `evidence/`. A real frame that a test needs, such as the frame proving that an oracle
  can see a bug, is copied into `scripts/testdata/<what-it-shows>/` and tracked as a fixture. Fixtures pass the
  device-secrets gate like any other staged file.
- A document never cites a path under `evidence/`. It restates the measurement or names the script.

This supersedes rule 2's "commit them" and the "evidence committed" gate of rule 3. It amends
[ADR-0001](0001-split-durable-decisions-from-ephemeral-build-evidence.md), which counted `evidence/` among
the tracked ephemeral records, and ADR-0015's "captured but never committed" clause, which now covers the whole
directory.

## Consequences

**This forbids** tracking any file under `evidence/`, and any test or document that depends on one.

A future reader will be tempted to commit "just this one" screenshot to show a fix. That screenshot is
either a fixture a test asserts on, kept in `scripts/testdata/`, or a picture nobody can rerun. The second
kind belongs in a PR description, not in the tree.

The record of how an old step was reached is its commit body and, until history is rewritten, the old
commits. The local `evidence/` directory is a working cache. Losing it loses nothing that a tracked file
depends on.
