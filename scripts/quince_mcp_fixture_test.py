"""The MCP server the Chat's MCP demo puts in the workspace - see `.maestro/fixtures/quince_mcp.py`.

On the tablet opencode starts it and nothing reports a server that failed to answer (opencode's
`registerMcpServers` ignores a failed `mcp.add`), so the exchange is proven here, on the host.
"""

import json
import subprocess
import sys
import unittest
from pathlib import Path

FIXTURE = Path(__file__).resolve().parent.parent / ".maestro" / "fixtures" / "quince_mcp.py"

INITIALIZE = {
    "jsonrpc": "2.0",
    "id": 0,
    "method": "initialize",
    "params": {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "t", "version": "1"}},
}
INITIALIZED = {"jsonrpc": "2.0", "method": "notifications/initialized"}
LIST = {"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}
CALL = {"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name": "marmalade_code", "arguments": {}}}
UNKNOWN = {"jsonrpc": "2.0", "id": 3, "method": "resources/list", "params": {}}


def exchange(*requests: dict[str, object]) -> list[dict[str, object]]:
    """Sends each request on one line of the fixture's stdin, and reads one response per line."""
    sent = "".join(json.dumps(r) + "\n" for r in requests)
    finished = subprocess.run(
        [sys.executable, "-I", str(FIXTURE)], input=sent, capture_output=True, text=True, timeout=10, check=True
    )
    return [json.loads(line) for line in finished.stdout.splitlines()]


class QuinceMcpFixtureTest(unittest.TestCase):
    def test_initialize_echoes_the_clients_protocol_version_and_offers_tools(self):
        (reply,) = exchange(INITIALIZE)
        self.assertEqual(reply["id"], 0)
        self.assertEqual(reply["result"]["protocolVersion"], "2025-06-18")
        self.assertIn("tools", reply["result"]["capabilities"])
        self.assertEqual(reply["result"]["serverInfo"]["name"], "quince")

    def test_a_notification_gets_no_reply(self):
        self.assertEqual(exchange(INITIALIZED), [])

    def test_lists_the_one_tool(self):
        (reply,) = exchange(LIST)
        (tool,) = reply["result"]["tools"]
        self.assertEqual(tool["name"], "marmalade_code")
        self.assertEqual(tool["inputSchema"]["type"], "object")

    def test_calling_the_tool_answers_the_code(self):
        (reply,) = exchange(CALL)
        self.assertEqual(reply["result"]["content"], [{"type": "text", "text": "marmalade-7349"}])
        self.assertFalse(reply["result"]["isError"])

    def test_an_unknown_method_is_an_error_naming_it(self):
        (reply,) = exchange(UNKNOWN)
        self.assertEqual(reply["error"]["code"], -32601)
        self.assertIn("resources/list", reply["error"]["message"])

    def test_answers_a_whole_session_in_order(self):
        replies = exchange(INITIALIZE, INITIALIZED, LIST, CALL)
        self.assertEqual([r["id"] for r in replies], [0, 1, 2])


if __name__ == "__main__":
    unittest.main()
