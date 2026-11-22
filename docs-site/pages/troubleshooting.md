# Troubleshooting

When something looks wrong, two places hold the evidence: **Settings → Diagnostics**, the app's
own record of its crashes and errors, and each Space's server log, the account the Space's
`jupyter_server` keeps of its own run. This page says what each one is, where it lives, and what
a person can do with it.

## Cells echo their source instead of running

The runtime ships for **arm64 only**. On any other architecture the app still installs and the
whole interface works, but a Space falls back to a fake kernel gateway that echoes each cell's
source back as its output — a cell that "runs" and prints its own code has not executed. The
release APK itself is `arm64-v8a`; [Getting started](getting-started.md) covers the install
and the Android 12L (API 32) floor.

## The Diagnostics error log

**Settings → Diagnostics** counts what the app has recorded about its own failures and offers
the two things a person can do with the record: share it, or clear it.

![Settings' Diagnostics section on a tablet with the entries a crash and the next launch
recorded, Share log and Clear logs beneath](images/diagnostics-entries-light.png#only-light)
![Settings' Diagnostics section on a tablet with the entries a crash and the next launch
recorded, Share log and Clear logs beneath](images/diagnostics-entries-dark.png#only-dark)

What it records is written by the app as things happen: an uncaught exception is a `CRASH`,
the platform's own account of the process's death covers what no handler ever saw — an `ANR`,
a native crash, a signal — and errors the app chooses to keep are `ERROR` entries. Every entry
carries its timestamp, kind, exception class, message, stack and thread, plus the app version,
device model and Android version it happened on.

The log lives outside the app, one JSON-lines file per UTC day —
`errors-yyyy-MM-dd.jsonl` in the app-specific external files directory
(`/sdcard/Android/data/com.jupydroid.app/files/`), readable with `adb pull` and no root. An
entry is scrubbed of secrets **before it is written**, because on older Android versions
another app holding storage permission can read that directory. Two caps keep it small on its
own: 1 MiB across the whole log and 7 days, oldest days dropped first, and a day that reaches
the byte cap stops taking entries rather than growing.

**Share log** copies every day's file, oldest first, into a single `error-log.jsonl` and hands
it to Android's share sheet — the copy is what leaves the device, and only when a person picks
a destination. **Clear logs** asks first, and the question states what it frees — the entry count and byte
size it is about to delete — before removing every day file.

![The Clear logs confirmation on a tablet stating the entries and size the clear frees, with
Cancel and Clear logs](images/diagnostics-clear-confirm-light.png#only-light)
![The Clear logs confirmation on a tablet stating the entries and size the clear frees, with
Cancel and Clear logs](images/diagnostics-clear-confirm-dark.png#only-dark)

![Settings' Diagnostics section after the clear on a tablet: 0 entries · 0 B](images/diagnostics-cleared-light.png#only-light)
![Settings' Diagnostics section after the clear on a tablet: 0 entries · 0 B](images/diagnostics-cleared-dark.png#only-dark)

## A Space's server log

Each Space keeps its own account of its `jupyter_server`, and it is **not** in the error log —
it is a file inside the Space. While a Space runs, the live log is
`files/spaces/<space-id>/run/jupyter_server.log`, beside the `jpserver-*.json` connection files
in the same `run/` directory; the server writes it at `DEBUG` level, its chattiest setting,
because the server's own account is the half of every kernel investigation the app cannot see
from the outside.

`run/` is per-run: at each start the launcher first renames a non-empty log into the Space's
archive — `files/spaces/<space-id>/server-logs/jupyter_server-<timestamp>.log`, stamped to the
millisecond — and then empties `run/` for the new server, so a restart never overwrites the run
before it, including the one that died. The archive prunes under the same 1 MiB / 7-day caps as
the error log.

Both directories sit in the app's private storage, so reading them takes `adb` on a build that
allows `run-as`:

```sh
adb shell run-as com.jupydroid.app cat files/spaces/base/run/jupyter_server.log
```

A release build does not allow `run-as`; the Diagnostics log above is the part of the record a
person can reach without a development build.

For the settings the Diagnostics section lives under, see [Settings](settings.md); for what a
Space is, see [Spaces](spaces.md).
