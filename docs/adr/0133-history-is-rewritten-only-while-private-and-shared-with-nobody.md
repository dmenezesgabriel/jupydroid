# ADR-0133 — Rewrite history only while the repository is private and shared with nobody

Status: accepted
Date: 2026-11-03

## Context

[ADR-0015](0015-rewrite-history-once-to-take-the-device-out-of-it.md) rewrote history once, before any
push, and said the rewrite "does not generalise". A later sweep of the same, still private, repository
found what that rewrite did not reach:

- **The host's home path** in 462 commits whose diff added or removed it, and in 5 message lines.
- **A Jupyter server token** in 105 paths, 8 rules' worth of carriers.
- **A personal email address** as the author and committer of every commit.
- **Ephemeral ids** (step codes) in 1,094 script, flow and fixture names.

Nobody had cloned the repository, so another rewrite still cost nobody a re-clone. The question was how
to do it without leaving the old objects reachable, and when a rewrite stops being an option.

## Decision drivers

- A stranger who clones the repository, once it is public, finds none of the above in any commit.
- The old objects must not stay fetchable by SHA from the published remote.
- The procedure is rerunnable and tested, not a pasted snippet.
- Nothing the owner still needs is deleted.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Force-push the rewrite over the existing remote | One repository | GitHub keeps the old objects fetchable by SHA until it collects them |
| Rewrite and delete the old repository | Nothing old remains | The 40 newest commits existed only on one disk; a delete cannot be undone |
| Rewrite into a new private repository; keep the old one private as `jupydroid-archive` | Published history never held the old objects; nothing deleted | The archive stays and must stay private |
| Leave it and rotate what leaked | No rewrite | A home path and an email address cannot be rotated |

## Decision

**A rewrite is affordable while the repository is private and shared with nobody, and never after it is
public. After that, the answer to a leak is to rotate what leaked.** This supersedes the "does not
generalise" clause of ADR-0015, which gave the reason (a re-clone for every collaborator) but not the
line.

The rewrite that this rule allowed:

- **Procedure.** `scripts/rewrite_history.py` builds the `git filter-repo` argv from the rules (the token
  carriers, `.sensitive-strings.local`, the personal-data scrub rules) and removes `evidence/` and
  `*.pyc`. `scripts/read_history_markers.py` counts the markers in every commit and exits non-zero on any
  hit. `scripts/remap_cited_shas.py` rewrites SHAs cited in tracked text from the `commit-map`. Each has
  a `_test.py`.
- **Identity.** Every commit carries `Gabriel Menezes <50274255+dmenezesgabriel@users.noreply.github.com>`,
  applied through a mailmap generated at run time and never tracked, since a tracked one would hold the
  address it removes. The name stays: the docs and ADRs carry it. The old address is also the profile's
  public email, so this stops a bulk harvest from a clone and nothing more.
- **Remote.** The rewritten history went to a new private repository that never held the old one. The old
  repository was renamed `jupydroid-archive` and stays private. Nothing was deleted on GitHub.
- **Ids stay in old commits.** The rewrite scrubs what must not be published; it does not rename files or
  edit comments in history. An id in a past commit is not secret.
- **Kept on purpose.** The upstream author's address in the MIT notice of `tools/grammars/python/`, which
  the licence requires, and the `archive/p2v-declined-attempt` tag, which was rewritten with everything
  else.

## Consequences

**This forbids** rewriting the history of a public repository, and any rewrite that is not rehearsed on a
bundle clone first and checked by `scripts/read_history_markers.py`.

The tempting "fix" is to run the rewrite again after a leak is found in the public repository. Every
clone and fork keeps the old objects, and GitHub keeps them fetchable by SHA, so the rewrite hides
nothing and breaks every collaborator's checkout. Rotate the credential, and treat a path or an address as
published.

`jupydroid-archive` holds the original history, including everything this ADR scrubbed. It must never be
made public, and nothing is cited from it.
