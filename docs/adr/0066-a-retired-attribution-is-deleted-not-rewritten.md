# ADR-0066 — Delete a retired attribution; do not rewrite it as a commit SHA

Status: accepted
Date: 2026-09-29

## Context

[ADR-0001](0001-split-durable-decisions-from-ephemeral-build-evidence.md) banned plan/step codes,
`docs/PLAN.md`, `docs/DECISIONS.md`, `evidence/<step>/` and `reference/…` from comments, and
`scripts/check_doc_citations.py` enforces it. It did not say what a comment that *has* one should
become. 600 citations across 291 of 1,131 source files (26%) are still waiting on that answer, and
the shapes differ:

| Kind | Citations | Shape |
|---|---|---|
| plan/step code | 449 | attribution — `since P2u-3`, `the rule a flow cost us in P2b`, `P5e-0 proved` |
| retired family (`X*`, `U*`) | 47 | same |
| `docs/DECISIONS.md` / `docs/PLAN.md` | 66 | pointer into a deleted file |
| `evidence/<step>/` | 38 | pointer at a committed measurement |

**What comparable projects do, measured.** Five of this repo's reference implementations, counting
source files that cite each kind of source:

| Repo | Source files | Cite a tracker URL | Cite an in-repo `.md` | Cite a commit SHA |
|---|---|---|---|---|
| vscode | 13,066 | 608 (4%) | 18 (0.1%) | 3 |
| jupyterlab | 979 | 49 (5%) | 0 | 0 |
| intellij-community | 187,158 | 167 (0.1%) | 2 (0.001%) | 8 |

```sh
cd <your vscode clone>
git grep -IEl 'https?://[^ ]*(issues?|bugs?)[/=][0-9]+' -- '*.ts' | wc -l
git grep -IEl '(see|per|from) [A-Za-z0-9_/.-]+\.md' -- '*.ts' | wc -l
git grep -IEl 'commit [0-9a-f]{7,40}' -- '*.ts' | wc -l
```

Three facts fall out. Citing an in-repo process document is not a convention anyone follows — it is
statistically absent. A tracker citation is a **stable external URL** attached to future work, which
is the [Google style guide](https://google.github.io/styleguide/pyguide.html)'s `TODO: crbug.com/12345678 - <what>`
and [Chromium's `TODO(crbug.com/…)`](https://groups.google.com/a/chromium.org/g/chromium-dev/c/whBCkcY8xtA),
adopted there precisely because a person's name goes stale. And a comment naming the commit that
produced the line is vanishingly rare: 3 files in 13,066.

Provenance is not absent from those projects — it is in version control. The kernel puts it there by
rule: `Fixes:` carries "at least the first 12 characters of the SHA-1 ID, and the one line summary",
and "the explanation body will be committed to the permanent source changelog, so should make sense
to a competent reader who has long since forgotten the immediate details"
(<https://docs.kernel.org/process/submitting-patches.html>). A comment that says *when* a line
arrived duplicates `git log -S` and rots; the commit message does not.

**A SHA map cannot be built for this repo's codes.** Commit subjects here end in `(<code>)`, so the
map is testable:

```sh
git log --all --format='%h%x09%s' | grep -F '(P5e-0)'
```

Of 193 distinct cited codes, 83 (203 citations) have exactly one such commit, 10 have several, and
**100 (253 citations) have none** — they are phase-level codes spanning many commits. Half the
citations have no SHA to be rewritten to, so a uniform rewrite would have to invent one.

**`evidence/` rots too, and is measured.** It is committed — 2,562 files in 264 directories — so a
path does resolve in a fresh clone, which was the argument for exempting device tests. But
directories are renamed and deleted as phases are re-lettered: 20 of 264 (7.6%) have been, and 2 of
the 38 currently cited paths (`evidence/p5e-1`, `evidence/p1-lifecycle`) already point at nothing.

```sh
git log --all --diff-filter=DR --name-status --format='' -- evidence |
  grep -E '^[DR]' | cut -f2 | cut -d/ -f2 | sort -u | wc -l
```

## Decision drivers

- A comment must be true in a fresh clone, years later, to a reader who never saw the plan.
- The fact is the durable part; where it was first written down is not.
- No invented mapping: a substitution that cannot be verified is worse than a deletion.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Delete the attribution, keep the fact | matches every reference repo (0% cite an internal doc); no new vocabulary; verifiable by the existing gate | loses "which change introduced this" from the comment — recoverable with `git log -S` |
| Rewrite every code as `Fixes: <sha> ("subject")` | kernel-standard; permanent | only 203 of 482 code citations resolve to one commit; 253 have none. Would also put SHAs in ~450 comments where the references put 3 in 13,066 |
| Keep the codes, document the retired families in `docs/README.md` | no churn | the codes were never stable (`P4e-P4h, which were P4d until the terminal phase was re-lettered`); this is the state ADR-0001 measured as 70% infection |
| Exempt `androidTest/` from the `evidence/` half | a device test's header genuinely points at a real artifact | 7.6% of those directories have already been renamed or deleted, and 2 of the 38 cited are dead today |

## Decision

**A retired attribution is deleted and the sentence keeps the fact.** `Neither states a foreground,
since P2u-3e.` becomes `Neither states a foreground.` — the reason follows it either way, and the
reason is what a reader needs.

Three narrow exceptions, in the order to reach for them:

1. The fact is a *decision* → cite `ADR-NNNN`. This is what the 66 `docs/DECISIONS.md` and
   `docs/PLAN.md` pointers become.
2. The fact is a *measurement* → restate the number and the command or test that produces it.
   `80dp against 32dp (MarkdownCellTest.anOpenHeadingsSourceStartsWhereProseDoes)` needs no
   `evidence/` path: the assertion is the evidence, and it is executable.
3. The line exists *because of* a specific defect, and naming the change is the only way to state
   why → `Fixes: <12-hex> ("<subject>")`, the kernel's form. Reserved for the case where deleting
   the attribution would leave the comment unable to explain itself. Expect single digits.

Future work named in a comment states the limitation and stops there; the item lives in
`docs/backlog.md`, whose row is ephemeral by design and therefore uncitable. Nothing here introduces
a `TODO(<id>)` convention: this repo has no public tracker, and the reference repos' tracker URLs are
stable in a way a local markdown row is not.

## Consequences

The remaining sweep is mostly **subtraction**, which is why it is a hand pass and not a regex pass —
a deletion mid-sentence changes the sentence. A regex attempt at rewording produced
`[Copy] is the same shape and has been already.` and was reverted; the safe mechanical shapes are
trailing parentheticals and whole clauses delimited by `—` or `,`, and everything else is read.

The baseline shrank as the sweep proceeded and reached **zero**: 600 citations across 291 files,
then 199 more that a narrow pattern had been hiding, are gone, and
`scripts/doc-citations-baseline.txt` is deleted with them. The gate now checks every file it
scans. `--write-baseline` remains for the one case that legitimately re-lists files — widening one
of the gate's own patterns, as fixing `P6b-0's` and `P5e-3/P5e-5` did — and a commit that uses it
says how many files it added.

`git log -S '<symbol>'` becomes the way to find out when a line arrived, and `git log --all --grep`
still finds the step codes in commit subjects, where they remain correct — a commit subject is
immutable, which is exactly why the codes are allowed to survive there and nowhere else.

The temptation to re-add "which step did this" is answered here rather than re-argued: the code was
retired because its numbering never was stable, and 253 of the citations name a code no single
commit corresponds to.

**The sweep this ADR describes was scoped to shipped code, and that left 233 attributions standing.**
`app/src/` and `tools/` reached zero, and `.maestro/` (91 lines), `scripts/` (31) and `evidence/`
(110) did not, because the gate reads only the first two trees. Each was closed the way this ADR
says - the sentence kept its fact, and the attribution either went or became an id that resolves:
ADR-0046 for the differential rule, `architecture/cross-cutting.md` §8.6 for what a Maestro flow
cannot read off a `Canvas`, `CLOSED-006` for `stty size`'s fixed answer, ADR-0002 for the exec
model. `scripts/check_retired_doc_citations.py` now gates all of it over every tracked file, so the
next deletion cannot leave the same residue.
