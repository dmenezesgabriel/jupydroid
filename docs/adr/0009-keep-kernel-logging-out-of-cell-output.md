# ADR-0009 — Keep kernel logging out of cell output, and assert it

Status: accepted
Date: 2026-08-14

## Context

Turning on kernel logging is the obvious first move when diagnosing anything, and the obvious fear
is that it will appear in the user's notebook. Three links decide it, all verified in the shipped
tree:

- `jupyter_client/launcher.py` leaves `stdout`/`stderr` as `None` off Windows, so the kernel
  **inherits** the server's.
- `JupyterServerLauncher` redirects the server's output to `run/jupyter_server.log`.
- `ipykernel/kernelapp.py` detects a log handler bound to fd 2 and reroutes it to the raw file
  descriptor — *"Seeing logger to stderr, rerouting to raw filedescriptor"*.

So kernel logs land in the server's log file, not in the notebook.

Measured once the kernel was actually put at DEBUG: 29 KB of kernel DEBUG in `jupyter_server.log`
and nothing on screen.

## Decision drivers

- Diagnosis must be cheap, or it does not happen.
- A property this app depends on but does not own must be asserted, not trusted.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep kernel logging off | Nothing can leak | Diagnosis becomes expensive, which is how six sightings of one defect produced no server-side account |
| Turn it on and trust upstream | Free | It is upstream behaviour across three packages; an upgrade could change it silently |
| Turn it on and assert the property | Diagnosis is cheap and a regression is caught | One more assertion to maintain |

## Decision

Kernel logging may be raised freely. Because the no-leak property is **upstream behaviour rather
than ours**, the `step10-run-cell-real-kernel` flow asserts it — `assertNotVisible: ".*MESSAGE
TYPE.*"` after a run — instead of trusting that an ipykernel upgrade will keep it.

## Consequences

The relationship between the three links is load-bearing: redirecting the server's output is what
puts the kernel's log in a file, because the kernel inherits it. A change to how
`JupyterServerLauncher` handles the server's streams changes where kernel logs go.

The thing a future reader will be tempted to "fix" is the `assertNotVisible` in a flow about running
a cell, which looks unrelated to that flow's subject. It is there because that is the only flow
where a real kernel logs while a notebook is on screen.
