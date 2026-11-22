# Chat

**Chat** is the rail panel where you talk to an agent inside the Space. Chats are `.chat` files
at the workspace root — the same files the [Files](files.md) panel would list, though the panel
reads them itself — so a chat outlives the app: close Jupydroid and reopen it, and every chat is
still there.

![The Chat panel on a tablet listing the workspace's chats — untitled, notes and untitled1 — with New chat and MCP servers beside them](images/chat-list-light.png#only-light)
![The Chat panel on a tablet listing the workspace's chats — untitled, notes and untitled1 — with New chat and MCP servers beside them](images/chat-list-dark.png#only-dark)

## Chats

The panel opens on the workspace's chats under **Open an existing chat:**, named without the
`.chat` ending. **New chat** starts an `untitled` chat; tapping a row opens it, and **All chats**
leads back to the list. Each message shows who sent it — you on one side, the agent on the other —
and the exchange is kept in the chat's file as it happens.

![A new chat on a tablet with the folded control chip beside the input reading OpenCode/Big Pickle](images/chat-new-light.png#only-light)
![A new chat on a tablet with the folded control chip beside the input reading OpenCode/Big Pickle](images/chat-new-dark.png#only-dark)

## Agent, mode and model

Once a [harness](spaces.md) is installed and its agent answers, one chip beside the input names
the model in use. Tapping it opens every control the agent offers: which agent replies, the
session mode — opencode's `build` and `plan` — and the model, whose list has a search field since
an agent signed in to a provider can offer hundreds. A pick applies to the questions you send
next; the agent's own default stands for anything you leave alone.

![The chip's menu on a tablet listing Agent: OpenCode, Session Mode with build and plan, and the searchable Model list](images/chat-controls-light.png#only-light)
![The chip's menu on a tablet listing Agent: OpenCode, Session Mode with build and plan, and the searchable Model list](images/chat-controls-dark.png#only-dark)

Typing `/` in the input lists the commands the agent advertises — opencode's include `/init`,
`/review` and `/customize-opencode` — and picking one fills the input with it.

![The chat input on a tablet with "/" typed and the agent's command list open beneath it](images/chat-slash-light.png#only-light)
![The chat input on a tablet with "/" typed and the agent's command list open beneath it](images/chat-slash-dark.png#only-dark)

## Tool calls and permissions

When the agent uses a tool, the call is a row in its answer: `•` while it runs, `✓` once it
completed, `✗` once it failed — including a call you rejected. A call the agent asks permission
for shows the agent's own options under `└─ Allow?` — opencode's shell commands offer **Allow
once**, **Always allow** and **Reject** — and the row names what was chosen afterwards, as
`- Allow once` or `- Reject`.

![A chat on a tablet whose answer ran a read and two shell commands — one allowed once, one rejected — and now waits on an edit with Allow once, Always allow and Reject offered](images/chat-transcript-light.png#only-light)
![A chat on a tablet whose answer ran a read and two shell commands — one allowed once, one rejected — and now waits on an edit with Allow once, Always allow and Reject offered](images/chat-transcript-dark.png#only-dark)

## Queued questions

While the agent is answering, a question you send is not lost: it waits in a queue above the
input, one pill each under an `N queued` count. A pill's pencil opens its question for editing —
**Save** keeps the change — and its `×` drops it. The questions are asked one at a time, oldest
first, each after the reply before it ends; they are kept per chat, so a queue survives a restart.

![A chat on a tablet with two questions waiting in the queue above the input](images/chat-queued-light.png#only-light)
![A chat on a tablet with two questions waiting in the queue above the input](images/chat-queued-dark.png#only-dark)

A chat needs an agent to answer: install a harness under **Harnesses** in the Space's settings,
and the agent keeps its own sign-in. With no agent the panel still lists and opens chats; it
just has no one to ask.

For the panel's place in the chrome see [Interface](interface.md); for the files the chats are,
see [Files](files.md); for the MCP servers an agent can be given, see [MCP servers](mcp.md).
