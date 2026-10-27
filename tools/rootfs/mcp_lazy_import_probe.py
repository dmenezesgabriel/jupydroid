"""Proves importing jupyter_server_mcp loads neither fastmcp nor uvicorn.

Run at image build time, after the strip, so the bytecode tested is the bytecode shipped. A server
start imports every enabled extension before it writes the runtime file the app waits on, and
jupyter_server_mcp was 3.90 s of that on the tablet, almost all of it in `extension.py` importing
`MCPServer` (fastmcp, uvicorn, starlette) at module scope. Warm, `server.starting` to
`server.listening` went from 3.41-3.67 s to 7.54-7.96 s (`scripts/cold_path_report.py`).
mcp_lazy_import_patch.py moves that import into `start_extension`, which runs once the server is
listening. mcp_bearer_probe.py, run after this, proves a started server still serves.
"""

import sys

import jupyter_server.serverapp  # noqa: F401 - the server has loaded it before any extension
import jupyter_server_mcp  # noqa: F401
from jupyter_server_mcp.extension import MCPExtensionApp

HEAVY = ("fastmcp", "uvicorn", "starlette", "mcp")


def loaded_heavy_modules() -> list[str]:
    return sorted(name for name in sys.modules if name.split(".")[0] in HEAVY)


at_import = loaded_heavy_modules()
assert not at_import, f"importing jupyter_server_mcp loaded {at_import[:5]}, expected none of {HEAVY}"
assert callable(MCPExtensionApp.start_extension), "MCPExtensionApp has no start_extension to defer into"
print(f"jupyter_server_mcp imports without {', '.join(HEAVY)}")
