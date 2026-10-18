"""Moves jupyter_ai_acp_client's `BaseAcpPersona` import into the one function that reads it.

`routes.py` imports `BaseAcpPersona` at module scope for a single `isinstance` test inside
`PermissionHandler._find_client_for_session`, and the extension imports `routes` to list its
handler, so every server start loads `acp` and pydantic: 0.85 s of the 1.22 s this extension cost
on the tablet (`python3 -X importtime`). Deferred, it loads on the first permission a person answers,
by which time a persona has loaded it anyway. jupyter_ai_acp_client 0.3.3 `routes.py`; unchanged on
https://github.com/jupyter-ai-contrib/jupyter-ai-acp-client at b30a6fb22e81.

Each edit must match exactly once, so a release that changes the file fails the image build here,
which is the moment to check whether upstream has made the cut and this can go.
"""

import importlib.util
import pathlib
import sys

MODULE_SCOPE_IMPORT = "\nfrom .base_acp_persona import BaseAcpPersona\n\n"
FUNCTION_START = '        Returns the client or None.\n        """\n'
FUNCTION_START_WITH_IMPORT = FUNCTION_START + "        from .base_acp_persona import BaseAcpPersona\n"


def routes_path():
    spec = importlib.util.find_spec("jupyter_ai_acp_client")
    return pathlib.Path(spec.origin).with_name("routes.py")


def replace_once(source, old, new):
    count = source.count(old)
    if count != 1:
        sys.exit(f"routes.py holds {count} copies of {old!r}, expected exactly 1")
    return source.replace(old, new)


path = routes_path()
source = replace_once(path.read_text(), MODULE_SCOPE_IMPORT, "\n")
path.write_text(replace_once(source, FUNCTION_START, FUNCTION_START_WITH_IMPORT))
print(f"deferred BaseAcpPersona's import in {path}")
