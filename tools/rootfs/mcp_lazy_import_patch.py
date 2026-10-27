"""Moves jupyter_server_mcp's `MCPServer` import into `start_extension`, the one function that uses it.

`extension.py` imports `MCPServer` at module scope, and with it fastmcp, uvicorn and starlette, so every
server start pays for them before it writes the runtime file the app waits on: 3.90 s on the tablet
(`Extension package jupyter_server_mcp took 3.9009s to import`, the server's own log). Deferred, they
load when the extension starts, which jupyter_server does once it is serving. A failure there is
logged by jupyter_server's `ExtensionManager.start_extension` and leaves the server serving with no
tools, as a failure in `MCPServer(...)` already did. jupyter-server-mcp 0.4.1 `extension.py`;
unchanged on https://github.com/jupyter-ai-contrib/jupyter-server-mcp at d1823a5cdd1b.

Each edit must match exactly once, so a release that changes the file fails the image build here,
which is the moment to check whether upstream has made the cut and this can go.
"""

import importlib.util
import pathlib
import sys

MODULE_SCOPE_IMPORT = "\nfrom .mcp_server import MCPServer\n"
FUNCTION_START = '        """Start the extension - called after Jupyter Server starts."""\n        try:\n'
FUNCTION_START_WITH_IMPORT = FUNCTION_START + "            from .mcp_server import MCPServer\n\n"


def extension_path() -> pathlib.Path:
    spec = importlib.util.find_spec("jupyter_server_mcp")
    return pathlib.Path(spec.origin).with_name("extension.py")


def replace_once(source: str, old: str, new: str) -> str:
    count = source.count(old)
    if count != 1:
        sys.exit(f"extension.py holds {count} copies of {old!r}, expected exactly 1")
    return source.replace(old, new)


path = extension_path()
source = replace_once(path.read_text(), MODULE_SCOPE_IMPORT, "\n")
path.write_text(replace_once(source, FUNCTION_START, FUNCTION_START_WITH_IMPORT))
print(f"deferred MCPServer's import in {path}")
