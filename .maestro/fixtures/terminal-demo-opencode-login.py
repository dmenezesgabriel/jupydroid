"""Runs `opencode auth login` on a pty of its own, and keeps every byte it writes.

Runs inside base's terminal, under proot. The terminal draws what a program printed, not how, so a
URL a tap opens could have come from the printed text or from an OSC 8 link around it. This keeps
the bytes in opencode-login.raw beside this script, for scripts/terminal-demo-driver.sh to count
`ESC ] 8 ;` in. The pty is interactive: keys typed into the terminal reach opencode's prompts.

    python3 terminal-demo-opencode-login.py
"""

import os
import pty
import sys

RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "opencode-login.raw")


def main() -> None:
    # pty.spawn gives the child a pty with no size, and clack lays its prompts out by the columns.
    columns, rows = os.get_terminal_size(sys.stdin.fileno())
    command = f"stty rows {rows} cols {columns}; exec opencode auth login"
    with open(RAW, "wb") as raw:

        def keep(fd: int) -> bytes:
            chunk = os.read(fd, 1024)
            raw.write(chunk)
            raw.flush()
            return chunk

        pty.spawn(["sh", "-c", command], keep)


if __name__ == "__main__":
    main()
