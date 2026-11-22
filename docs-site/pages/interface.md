# Interface

Inside a running Space the window is a **rail** of icons, a **side panel** beside it, and a
**main area** of document tabs. The layout follows the window: a wide one gets the rail and a
panel at once, and a narrow one — a phone, or the tablet turned upright — swaps them for a
bottom bar whose **More** item opens the same destinations.

## The rail

At two panes the rail runs the window's full height. Its icons carry no text — each names
itself for a screen reader — and top to bottom they are:

- **Files**, **Sessions and Tabs** and **Search** — the workspace's own panels.
- **Source Control** and **Chat** — the panels extensions add, between Search and the rest.
- **Table of contents** and **Debugger** — the panels that belong to the open document.
- `+` opens the Launcher; **Spaces** and **Settings** sit at the rail's foot.

Picking the panel already showing collapses the side area back to the rail alone, giving the
document the full width.

![The workspace at two panes: the rail beside the Files panel, the Launcher filling the main area](images/interface-launcher-light.png#only-light)
![The workspace at two panes: the rail beside the Files panel, the Launcher filling the main area](images/interface-launcher-dark.png#only-dark)

## The side panels

The rail picks one panel at a time, and it fills the area beside it.

- **Files** — the workspace browser: folders, notebooks and files, with the New menu for
  creating them.
- **Sessions and Tabs** — the open documents beside the kernel sessions and terminals the
  server still holds, in three sections. A row opens what it names and its ✕ ends it; the list
  refreshes while the panel is showing.

![The Sessions and Tabs panel at two panes: Open Tabs, Kernel Sessions and Terminals](images/interface-sessions-light.png#only-light)
![The Sessions and Tabs panel at two panes: Open Tabs, Kernel Sessions and Terminals](images/interface-sessions-dark.png#only-dark)

- **Search** — searches the workspace's files, with match options and replace.

![The Search panel at two panes with results for cat in two files](images/interface-search-light.png#only-light)
![The Search panel at two panes with results for cat in two files](images/interface-search-dark.png#only-dark)

- **Table of contents** — the current notebook's headings; a tap jumps to the section and the
  folds collapse what a heading owns.

![The Table of contents panel at two panes listing the notebook's headings](images/interface-outline-light.png#only-light)
![The Table of contents panel at two panes listing the notebook's headings](images/interface-outline-dark.png#only-dark)

- **Debugger** — the stopped kernel's call stack and variables; it reads **Not stopped** until
  a session pauses.

![The Debugger panel at two panes before any session stops](images/interface-debugger-light.png#only-light)
![The Debugger panel at two panes before any session stops](images/interface-debugger-dark.png#only-dark)

- **Source Control** — the Git extension's panel: the branch and changed files of the
  directory the Files panel browses, or a note that the directory is not a repository.

![The Source Control panel at two panes reporting the workspace is not a Git repository](images/interface-source-control-light.png#only-light)
![The Source Control panel at two panes reporting the workspace is not a Git repository](images/interface-source-control-dark.png#only-dark)

- **Chat** — the AI extension's panel: the open chats, or **No chat opened** with **New chat**
  and **MCP servers**.

![The Chat panel at two panes with no chat opened](images/interface-chat-light.png#only-light)
![The Chat panel at two panes with no chat opened](images/interface-chat-dark.png#only-dark)

## Tabs

The main area's top edge is a strip of tabs — the Launcher, notebooks, text and Markdown
files, a Console, a Terminal — one per open document. Tap a tab to switch to it; its ✕ closes
it, and a dot in place of the ✕ marks unsaved changes.

![The tab strip at two panes over a notebook, with the Launcher and a Markdown file beside it](images/interface-tabs-light.png#only-light)
![The tab strip at two panes over a notebook, with the Launcher and a Markdown file beside it](images/interface-tabs-dark.png#only-dark)

Press and hold a tab for its menu: close the other tabs or the ones to its right, move the
document to the side panel where it has one, rename it, save it under a new name, revert to a
checkpoint, duplicate it or delete it.

![A tab's menu open at two panes: Close All Other Tabs through Delete…](images/interface-tab-menu-light.png#only-light)
![A tab's menu open at two panes: Close All Other Tabs through Delete…](images/interface-tab-menu-dark.png#only-dark)

## The Launcher

The `+` on the rail — and an empty main area at two panes — opens the **Launcher**, its cards
grouped **Notebook**, **Console** and **Other**: a new Python 3 notebook or console, a
Terminal, and new Text, Markdown or Python files. Tapping a card opens what it names in the
launcher's place; opening a document from the Files panel leaves the Launcher tab where it is.

## One pane: the bottom bar and More

Where the window is one pane there is no room for the rail, so a **bottom bar** under the panel
carries the same job: **Files**, **Sessions and Tabs**, **Search**, a `+` for the Launcher, and
**More**. The bar stands under the panels, not over a document — inside a document, back returns
to the browser first.

![The bottom bar at one pane under the Files panel](images/interface-one-pane-light.png#only-light)
![The bottom bar at one pane under the Files panel](images/interface-one-pane-dark.png#only-dark)

**More** opens a modal rail with what the bar has no room for: **New terminal**, the contributed
panels (**Source Control**, **Chat**), and **Spaces** and **Settings** at its foot. Back or a
tap outside closes it.

![The More rail open at one pane over the dimmed Files panel](images/interface-more-rail-light.png#only-light)
![The More rail open at one pane over the dimmed Files panel](images/interface-more-rail-dark.png#only-dark)

The bar's own items still switch the window at this width — Search fills it exactly as the
rail's pick does beside two panes.

![The Search panel at one pane, reached from the bottom bar](images/interface-one-pane-search-light.png#only-light)
![The Search panel at one pane, reached from the bottom bar](images/interface-one-pane-search-dark.png#only-dark)

Table of contents keeps its own route at one pane — **Show table of contents** in the
notebook's menu opens it over the document — and the Debugger's variables are the sheet the
control bar's toggle opens while a session is stopped.

To create and manage the Spaces this interface lives inside, see [Spaces](spaces.md). The
[first-run walkthrough](getting-started.md) shows the same chrome on a fresh install.
