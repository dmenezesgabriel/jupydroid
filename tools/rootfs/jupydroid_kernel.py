"""Kernel entry point for a jupydroid Space.

Stock ipykernel, with one thing turned off: the IOPub pipe that captures output
from processes the kernel itself forks.

Why
---
`IOPubThread._setup_pipe_in` binds `tcp://127.0.0.1` for that pipe, regardless of
the kernel's transport. On Android that call does not fail, it *aborts*: Android
denies binding a NETLINK_ROUTE socket, so `getifaddrs()` returns EACCES, and
libzmq turns that into `errno_assert` -> SIGABRT inside `ip_resolver.cpp`. The
guard ipykernel wraps around the bind is `except zmq.ZMQError`, which a SIGABRT
never reaches, so the kernel dies before it can serve anything.

`IOPubThread.__init__` takes `pipe=False` by default; only `kernelapp.init_iopub`
hardcodes it to `True`. This module selects the library's own default instead of
inventing behaviour, and touches no private method, so an ipykernel upgrade
cannot silently change what it does.

What this costs
---------------
Output written by processes the kernel *forks* - `multiprocessing` workers
printing to stdout, say - is not captured. Cell output, tracebacks, display_data
and everything on the normal execution path are unaffected. The kernel logs a
warning at startup so this is never a silent surprise; grep the kernel log for
`jupydroid`.

Evidence: evidence/step8.5/getifaddrs-root-cause.txt, choosing-the-fix.txt.
"""

from __future__ import annotations

import logging

import zmq
from ipykernel.iostream import IOPubThread
from ipykernel.kernelapp import IPKernelApp

__all__ = ["JupydroidKernelApp", "main"]

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


def main() -> None:
    JupydroidKernelApp.launch_instance()


if __name__ == "__main__":
    main()
