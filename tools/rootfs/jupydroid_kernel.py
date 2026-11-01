"""Kernel entry point for a jupydroid Space.

Stock ipykernel, with two things turned off: the IOPub pipe that captures output
from processes the kernel itself forks, and the debugger's own port lookup.

Why
---
`IOPubThread._setup_pipe_in` binds `tcp://127.0.0.1` for that pipe, regardless of
the kernel's transport. On Android that call does not fail, it *aborts*: Android
denies binding a NETLINK_ROUTE socket, so `getifaddrs()` returns EACCES, and
libzmq turns that into `errno_assert` -> SIGABRT inside `ip_resolver.cpp`. The
guard ipykernel wraps around the bind is `except zmq.ZMQError`, which a SIGABRT
never reaches, so the kernel dies before it can serve anything.

`ipykernel.debugger.DebugpyClient.get_host_port` hits the identical call -
`bind_to_random_port("tcp://127.0.0.1")` on a bare zmq socket, used only to find
a free loopback port before handing it to `debugpy.listen()`. The DAP bridge
does not need a *zmq* socket for that; a plain `socket.socket` bind-to-port-0
finds the same free port without ever calling `getifaddrs()` (ADR-0008).

`IOPubThread.__init__` takes `pipe=False` by default; only `kernelapp.init_iopub`
hardcodes it to `True`. This module selects the library's own default instead of
inventing behaviour, and touches no private method, so an ipykernel upgrade
cannot silently change what it does.

`IPythonKernel.debugger_class` is a traitlets `Type("ipykernel.debugger.Debugger")`,
resolved by importing `ipykernel.debugger` and reading its `Debugger` attribute the
first time a kernel asks for one. Replacing that attribute before any kernel is
constructed redirects the trait's own resolution - the same "point the existing
extension point at us" shape as the IOPub override above, without fighting
`IPKernelApp`'s own init ordering to inject traitlets config in time.

What this costs
---------------
Output written by processes the kernel *forks* - `multiprocessing` workers
printing to stdout, say - is not captured. Cell output, tracebacks, display_data
and everything on the normal execution path are unaffected. The kernel logs a
warning at startup so this is never a silent surprise; grep the kernel log for
`jupydroid`.

The debugger override changes no behaviour a DAP client can observe: the port it
finds is used exactly as `DebugpyClient.get_host_port`'s original caller expects,
handed straight to `debugpy.listen()`.

What was measured, and why each override is the narrowest one that works, is ADR-0008.
"""

from __future__ import annotations

import logging
import socket

import ipykernel.debugger as _ipykernel_debugger
import zmq
from ipykernel.debugger import Debugger, DebugpyClient
from ipykernel.iostream import IOPubThread
from ipykernel.kernelapp import IPKernelApp

__all__ = ["JupydroidDebugger", "JupydroidDebugpyClient", "JupydroidKernelApp", "main"]

_SUBPROCESS_OUTPUT_NOTICE = (
    "jupydroid: IOPub subprocess-output pipe disabled. Output from processes this "
    "kernel forks will not be captured. Cell output and tracebacks are unaffected. "
    "Reason: the pipe binds tcp://127.0.0.1, and on Android libzmq aborts on that "
    "because getifaddrs() is denied (EACCES on netlink bind)."
)


class JupydroidKernelApp(IPKernelApp):
    """IPKernelApp whose IOPub thread does not open the forked-subprocess pipe."""

    def init_iopub(self, context: zmq.Context) -> None:
        """Mirror of the base implementation with `pipe=False`.

        Deliberately a copy rather than a call to `super()` followed by surgery:
        the base method creates the thread and the pipe together, so there is no
        point at which the pipe could be removed without reaching into privates.
        """
        self.iopub_socket = context.socket(zmq.PUB)
        self.iopub_socket.linger = 1000
        self._apply_curve_server_options(self.iopub_socket)
        self.iopub_port = self._bind_socket(self.iopub_socket, self.iopub_port)
        self.log.debug("iopub PUB Channel on port: %i", self.iopub_port)
        self.configure_tornado_logger()

        self.iopub_thread = IOPubThread(self.iopub_socket, pipe=False, session=self.session)
        self.iopub_thread.start()
        self.iopub_socket = self.iopub_thread.background_socket

        # Warning, not debug: a missing capability that is never mentioned is
        # indistinguishable from a bug when someone is debugging lost output.
        self.log.warning(_SUBPROCESS_OUTPUT_NOTICE)
        logging.getLogger(__name__).warning(_SUBPROCESS_OUTPUT_NOTICE)


class JupydroidDebugpyClient(DebugpyClient):
    """DebugpyClient whose port lookup does not bind a zmq socket.

    `get_host_port` is only ever asked for a free loopback port to hand to
    `debugpy.listen()` (`Debugger.start`); the zmq bind-then-unbind the base
    implementation uses for that is incidental, not a DAP requirement, and it is
    the exact call that aborts (ADR-0008). A plain
    `socket.socket` bind to port 0 finds a free port the same way, without
    `getifaddrs()`.
    """

    def get_host_port(self) -> tuple[str, str]:
        """Mirror of the base implementation, sourcing the port from `socket` not `zmq`."""
        if self.debugpy_port == -1:
            probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            try:
                probe.bind((self.debugpy_host, 0))
                port = probe.getsockname()[1]
            finally:
                probe.close()
            self.endpoint = f"tcp://{self.debugpy_host}:{port}"
            self.debugpy_port = str(port)
        return self.debugpy_host, self.debugpy_port


class JupydroidDebugger(Debugger):
    """Debugger whose DebugpyClient is `JupydroidDebugpyClient`.

    The base `__init__` builds a plain `DebugpyClient` itself, with no extension
    point of its own - so this swaps the already-constructed instance's class
    rather than re-implementing `__init__`'s dozen other assignments to reach
    the one line that matters.
    """

    def __init__(self, *args: object, **kwargs: object) -> None:
        """Build a stock `Debugger`, then repoint its client at the fixed one."""
        super().__init__(*args, **kwargs)
        self.debugpy_client.__class__ = JupydroidDebugpyClient


# See "IPythonKernel.debugger_class" above for why this is a module-attribute swap
# rather than traitlets config.
_ipykernel_debugger.Debugger = JupydroidDebugger


def main() -> None:
    JupydroidKernelApp.launch_instance()


if __name__ == "__main__":
    main()
