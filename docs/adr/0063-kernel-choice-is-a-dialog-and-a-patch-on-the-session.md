# ADR-0063 — Kernel choice is `selectKernel`'s dialog and a `PATCH` on the session

Status: accepted
Date: 2026-09-27

## Context

The picker's rows are enumerated in the reference's `sessioncontext.tsx` (`kernelOptions`): start a new kernel
per spec, preferred language first; **"No Kernel" — which shuts the current kernel down**; and connect to an
already-running kernel.

`session.changeKernel` is `PATCH /api/sessions/<id>`, and `metadata.kernelspec` — which
`NotebookFileMetadata` already round-trips — is where the choice persists.

"Create a new environment" is vscode-jupyter's affordance, and **in this architecture an environment *is* a
Space** ([ADR-0010](0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md)).

## Decision drivers

- The kernel catalogue must be answerable while the server is stopped, which is most of the time.
- The choice must persist where a notebook already carries it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A picker over a list this app maintains | Works offline | Invents kernels `jupyter_server` cannot name |
| A second kernelspec store beside the notebook's metadata | Explicit | Two sources of truth for one choice; `metadata.kernelspec` already round-trips |
| The reference's dialog, `PATCH` on the session, choice in `metadata.kernelspec` | One source of truth, and the wire is the reference's | The catalogue has to answer with the server down |

## Decision

The picker is the reference's dialog; the change is `PATCH /api/sessions/<id>`; the choice persists in
`metadata.kernelspec`.

The catalogue is `KernelSpecs` — live `GET api/kernelspecs` while a server is up, and the Space's **declared**
list while it is not, which is what gives the picker an honest answer with the server stopped.

**"Create a new environment" is a Space**, and it lands with per-Space kernelspecs rather than as a second
environment model.

**One more fidelity, which the indicator flow made observable:** `kernelDisplayName` resolves the **pending**
name through the catalogue for as long as a connect or a `{name}` PATCH is out — never for an `{id}` attach,
whose model carries no name. Without it the bar read `Kernel No Kernel: Starting` during the very startup the
indicator exists to show.

## Consequences

**This forbids** a picker that invents kernels `jupyter_server` cannot name, and a kernelspec store parallel to
the notebook's own metadata.

The pending-name resolution is the kind of detail that looks like an optimisation and is a correctness fix; the
`{id}`-attach exception is load-bearing, because that model genuinely has no name to resolve.

The thing a future reader will be tempted to "fix" is "No Kernel", which reads as a no-op. It shuts the current
kernel down.
