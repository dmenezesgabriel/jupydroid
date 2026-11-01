# AUD-013 — What a stranger would read

Milestone: `M9` · State: in-progress · Depends on: `OPS-015` · Closes: —

> An **audit** item, not `_TEMPLATE.md` filled in: it ships a *measurement*, so there is no Outcome,
> Demo or Maestro section
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md),
> [ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)). It records; it repairs
> only a `defect`, red test first, and every other repair becomes an item of its own. `OPS-002` consumes
> its scrub list.
>
> **Deleted when the item ships** ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **everything a stranger can read once the repository is public**: every tracked file at
`HEAD`, and every blob and message in all 1475-and-counting commits. Four questions at once: what
personal data is there, in which class, where it sits (file, commit, message, pixel), and whether the
gate that keeps it out (`scripts/check_device_secrets.py`) would have caught it.

Classes, each counted by its own pattern so the number is produced, not stored:

| Class | Why it identifies someone |
|---|---|
| home path | `/home/<user>` names the account on the machine |
| author identity | name and email on every commit |
| email in text | a third party's address in a file |
| device identity | tablet serial, model, Wi-Fi, push tokens (covered by the gate today) |
| machine-specific notes | prose that only holds on one machine (`AGENTS.md`) |
| binary frames | screenshots whose pixels show any of the above (read by eye, not grep) |

## The surface, produced

```sh
python3 scripts/read_personal_data.py                 # hits per class over tracked files
git grep -l "/home/gabriel" | wc -l                   # files carrying the home path
git log --all -S"/home/gabriel" --oneline | wc -l     # commits whose diff adds or removes it
git log --all --format=%B | grep -c "/home/gabriel"   # commit-message lines
git log --format='%an <%ae>' | sort | uniq -c         # author identities
git grep -hoE "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[a-z]{2,}" | sort | uniq -c | sort -rn
```

## Open decisions

- **Author email on every commit.** Authorship, not a leak a file edit can fix. Keeping it, or
  rewriting through a mailmap to a noreply address, is `OPS-002`'s call; this item only counts it.
- `robert.einhorn.hu@gmail.com` (two files): upstream attribution or personal data? Read the
  files before classifying.

## Tasks

- [ ] T1 — `scripts/read_personal_data.py`: per-class hit counts over tracked files (+ `_test.py`).
- [ ] T2 — the scrub list (file, class, replacement) for `OPS-002`; the three PNG frames looked at;
  the email above classified.
- [ ] T3 — a home-path class in `scripts/check_device_secrets.py`, red test first; the
  machine-specific `AGENTS.md` note handed to `OPS-002`.
- [ ] T4 — close: backlog row edited, this PRD deleted.

## Out of scope

The history rewrite, the licence and the README (`OPS-002`–`OPS-004`).
