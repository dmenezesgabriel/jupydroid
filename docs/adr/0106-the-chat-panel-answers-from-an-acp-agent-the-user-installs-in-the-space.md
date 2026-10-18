# ADR-0106 — The Chat panel answers from an ACP agent the user installs in the Space

Status: accepted
Date: 2026-10-18

## Context

The second extension under [ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)
is jupyter-ai's chat. It is a side panel ([ADR-0105](0105-an-extension-contributes-a-side-panel-the-host-frames.md))
where the user asks a question and an AI answers. Building it left six questions: what answers,
how much of jupyter-ai the image ships, how the panel talks to the server, where the agent comes
from, which chat and persona the panel uses, and whose a message is.

The references:

- **jupyter-ai** (jupyter-ai@5b23f55d) is a metapackage. Its code lives in `jupyter-ai-contrib`
  packages, which were read from the wheels it resolves: `jupyterlab_chat` 0.25.2,
  `jupyter_ai_router` 0.1.1, `jupyter_ai_persona_manager` 0.2.2, `jupyter_ai_acp_client` 0.3.3,
  `agent-client-protocol` 0.11.1.
  - jupyterlab-chat serves a `.chat` file. It speaks Yjs over RTC, or a JSON WebSocket at
    `/api/chat/ws/<path>` when no RTC provider is enabled (`__init__.py`, `rtc_lib.py`).
  - The router hands each new message to the persona that `metadata["to_persona"]` names, and to none
    if it names none (`persona_manager.py`).
  - The ACP client's personas spawn an agent's own CLI and relay its stream back into one message, by
    id (`acp_personas/opencode.py`, `default_acp_client.py`). The alternative is Jupyternaut, which
    answers over litellm with a provider key.
- **VS Code** (vscode@3f3380c) owns a chat view in the secondary side bar. Extensions contribute
  participants and model providers to it
  (`src/vs/workbench/contrib/chat/browser/chatParticipant.contribution.ts`,
  `src/vscode-dts/vscode.d.ts` `registerLanguageModelChatProvider`).
- vscode-jupyter contributes tools to VS Code's chat and has no chat of its own. IntelliJ's AI
  Assistant is a separate, closed plugin. termux, winlator and the shipped rootfs have none.

2 of the 7 references have a chat. Both put it in a side panel that something plugged in answers,
and neither builds the model in.

## Decision drivers

- The app never holds a provider key.
- Every Space start pays for what the image loads, so nothing loads that a request does not use.
- The agent is the user's, installed into the Space, which is their own system.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| What answers | Jupyternaut over litellm; an ACP persona over an agent's CLI | ACP: the agent owns its provider, its sign-in and its tools. Jupyternaut needs a key in the app and 143 wheels (litellm, langchain, boto3, tokenizers) |
| What the image ships | the full metapackage, 210 wheels with JupyterLab 4.6; the ACP chain alone, 73 | the ACP chain alone. The rest drives a JupyterLab frontend this app does not have |
| Transport | Yjs over RTC; the JSON WebSocket | the JSON WebSocket. The image enables no RTC provider, so it is the one the server registers, and a CRDT client in Kotlin is not needed |
| Where the agent comes from | baked into the image; installed by the user in the Space | installed by the user. `opencode-linux-arm64-musl` is 195 MB unpacked, and the agents after it need Node |
| Which chat, which persona | a chat picker; one chat | one chat, `untitled.chat` at the workspace root, which is what jupyterlab-chat names the first chat in its default directory. Messages address `jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona`, which is `BasePersona.id` (`base_persona.py`) |
| Whose a message is | the sender id equals this socket's user; the chat's `users` mark the sender as no bot | no bot. Every socket is a new anonymous user (jupyter_server 2.20.0 `auth/identity.py`, `generate_anonymous_user`), so after a relaunch the user's own question would read as someone else's |

## Decision

**The Chat panel asks jupyter-ai's opencode persona over jupyterlab-chat's JSON WebSocket, and
opencode is installed and signed in by the user from the Space's own terminal. The image ships
jupyter-ai's ACP chain and nothing else of jupyter-ai.**

- `:extensions:ai` holds the `AgentChat` port, `OkHttpAgentChat` and `FakeAgentChat`, and the panel
  (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/`).
- The image adds 11 pins, each resolved for `aarch64-unknown-linux-musl` on Python 3.12 with
  binaries only (`tools/rootfs/requirements.lock`). The compressed rootfs grew by 4.1 MB (+4.8%) and
  the unpacked one by 16.8 MB (+4.1%). A build step fails unless all four server extensions are
  enabled and OK (`tools/rootfs/Dockerfile`).
- With the chain added, the warm server boot median went from 2.92 s to 4.66 s on SM-X216B.
  `python3 -X importtime` in the Space puts 98–99% of `jupyter_ai_acp_client`'s 1.22 s import behind
  one line. `routes.py` imports `BaseAcpPersona` at module scope for one `isinstance` test, and that
  loads `acp` and pydantic on every start. `tools/rootfs/acp_lazy_import_patch.py` moves the import
  into that function, and `tools/rootfs/acp_lazy_import_probe.py` gates that it stays out of the
  import. After that, the warm boot median is 4.00 s. The patch fails the image build when a release
  changes the file.
- opencode goes to `/usr/local/bin`, which is on the Space's `PATH`
  (`app/src/main/java/com/jupydroid/app/data/space/SpaceRuntime.kt`), so the persona's
  `shutil.which("opencode")` finds it. The persona manager checks a persona's requirement when a chat
  opens, so an install needs no server restart. `opencode auth login` signs it in.
- The panel follows the newest message's last line while the reader is at the end. That is
  jupyter-chat's rule (jupyter-chat@9e02bb70 `packages/jupyter-chat/src/components/messages/messages.tsx`).
  The panel draws no message that has an empty body.

## Consequences

- No key, sign-in or model choice appears anywhere in the app. Another ACP agent is a persona the
  image already ships plus the agent's own install. jupyter_ai_acp_client 0.3.3 has personas for
  claude (which needs `claude-agent-acp`, a Node package), codex, copilot, goose, kilo, kiro and
  mistral_vibe (`acp_personas/`). Antigravity has been reported to abort on 39-bit arm64 kernels
  (https://discuss.ai.google.dev/t/bug-agy-crashes-on-arm64/145425).
- A question that needs a tool waits. opencode's bundled config asks before every edit and shell
  command (`usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.json` in
  the shipped rootfs), and the panel cannot answer a permission request yet.
- Do not "fix" the lazy-import patch by removing it while upstream still has the module-scope
  import. That brings back 0.66 s on every warm start.
- Do not compare a sender to the socket's own user id to decide "mine". That identity is new on
  every connection.
