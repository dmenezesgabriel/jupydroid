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
python3 scripts/read_personal_data.py --scrub-rules   # the filter-repo rules, from this machine's home
git grep -l "$HOME" -- ':!evidence'                   # the files a replace-text cannot fix: edit by hand
git grep -l "$HOME" | wc -l                           # files carrying the home path
git log --all -S"/home/gabriel" --oneline | wc -l     # commits whose diff adds or removes it
git log --all --format=%B | grep -c "/home/gabriel"   # commit-message lines
git log --format='%an <%ae>' | sort | uniq -c         # author identities
git grep -hoE "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[a-z]{2,}" | sort | uniq -c | sort -rn
```

## Open decisions

- **Author email on every commit.** Authorship, not a leak a file edit can fix. Keeping it, or
  rewriting through a mailmap to a noreply address, is `OPS-002`'s call; this item only counts it.
- **Resolved: keep `robert.einhorn.hu@gmail.com`** (`tools/grammars/python/`, two files). It is the
  upstream author's address in an MIT notice the licence requires us to keep: attribution, not ours.
- **Resolved: the three PNG frames show nothing personal.** Two Settings screens and one emulator
  document; the status bar holds no SSID, name or account. The "email in a PNG" was `git grep`
  matching compressed bytes.
- **Hand edits before the rewrite:** `scripts/java-home.sh` *uses* its path, so it becomes `$HOME`, not
  `/home/user`; the `AGENTS.md` machine note becomes generic (T4). The
  `gabriel@tablet` terminal title in `MainAreaTabStripTest.kt` carries a first name: `user@tablet`.

## Tasks

- [x] T1 — `scripts/read_personal_data.py`: per-class hit counts over tracked files (+ `_test.py`).
- [x] T2 — the scrub list for `OPS-002` (`--scrub-rules`); the three PNG frames looked at; the email
  classified.
- [x] T3 — `home_rule(home)` in `scripts/check_device_secrets.py` (the host's `/home/<name>`, never
  stored) and the scrubber applying it, red test first. Split from the gate class: refusing the home
  turns the whole-tree sweep red until T4 cleans 1127 evidence files.
- [x] T4 — the tree scrubbed: evidence through `scripts/scrub_device_secrets.py`; `scripts/java-home.sh`
  uses `$HOME` (`scripts/java_home_test.py`); the `AGENTS.md` machine note made generic - no local
  file, since the script derives the path the note held and nothing would read one.
- [ ] T5 — the gate refuses the host's home in `main`; the whole-tree sweep stays green.
- [ ] T6 — close: backlog row edited, this PRD deleted.

## Out of scope

The history rewrite, the licence and the README (`OPS-002`–`OPS-004`).
