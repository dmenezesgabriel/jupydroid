# ADR-0023 — An unanswered greeting replaces the kernel, and is never asked twice

Status: accepted
Date: 2026-08-14

## Context

`kernel_info_request` going unanswered means the shell channel is not carrying replies, and asking
again does not change that. Measured: 2 stalls in 80 runs, with 120 asks over 60 s and not one
reply, while iopub delivered throughout ([CLOSED-004](../research/closed-questions.md)).

Every sighting cleared on a fresh kernel. The cause of the dead shell channel is still open and
parked as `GAP-007` ([`../research/execution-stall.md`](../research/execution-stall.md)); this
decision is the **repair**, which is a separate question from the diagnosis.

The reference's own behaviour is what produced the worst version of the symptom. JupyterLab sends the
pending messages anyway after its timeout — a request enqueued against a channel carrying nothing,
waited on forever, with no error raised anywhere.

## Decision drivers

- A stall must cost seconds and be visible, not hang forever and be silent.
- Never discard a user's variables to recover from something that has an answer.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Retry the greeting | The obvious move | Measured: 120 asks, no reply. It does not work |
| Send the pending messages anyway after a timeout | What the reference does | This *is* the defect: an unbounded silent wait |
| Restart the kernel, greet the replacement once, then fail the cell | Bounded, visible, and every sighting cleared on a fresh kernel | Loses the kernel's namespace |

## Decision

An unanswered greeting means a **restart followed by exactly one more greeting**. If the replacement
is no better, the cell **fails** rather than being handed to a channel known to be silent.

Measured over 50 tablet runs: 50 passed, 0 failed, and 1 was a real stall recovered in **1.4 s**.

Three consequences, each deliberate:

- **The socket is not reopened with it.** JupyterLab's `restart()` follows the REST call with
  `reconnect()` for "cases where kernel ports have changed", but `jupyter_server` calls
  `km.restart_kernel(kernel_id)` with `newports` defaulting to False, and its `on_kernel_restarted`
  only publishes a status. The endpoints do not move.
- **This departs from the reference**, which sends the pending messages anyway. That failsafe is what
  produced the unbounded stall.
- **Typing never triggers it.** `complete` waits on the same handshake, so recovery would otherwise
  discard the variables of a kernel someone had merely opened a notebook onto. A completion has an
  answer for "the kernel cannot say"; a cell does not.

## Consequences

A recovered stall costs about 1.4 s and a replaced kernel — so the user loses their variables, which
is a real cost accepted knowingly in exchange for never hanging.

Because recovery is bounded and reported, every stall is now *recorded*, which is what made the rate
measurable at all (≈2.5%, 2 in 81 runs) and what tells a future investigation how many runs an
experiment needs.

The thing a future reader will be tempted to "fix" is the asymmetry: a cell recovers and a completion
does not. Making completion recover too would throw away a namespace to answer a question that has a
perfectly good "don't know".
