# Spaces

A **Space** is an isolated system on the device: its own files, installed packages and Python
environments, each with its own `jupyter_server` running on loopback inside the app's bundled
Linux root filesystem. **Base** is bundled with the app and protected; you can add your own
Spaces beside it, and several can run at once.

## The Spaces home

The app opens on **Spaces**, which lists every Space on the device. Each row shows the Space's
name, its id and how long ago it was created, and whether it reads **Running** or **Stopped**.

![The Spaces home on a tablet: the bundled Base Space listed as stopped](images/spaces-home-light.png#only-light)
![The Spaces home on a tablet: the bundled Base Space listed as stopped](images/spaces-home-dark.png#only-dark)

Every row carries its own controls:

- **Run** starts the Space's server, **Stop** stops it.
- **Open** enters the Space's workspace and is enabled only while the row reads **Running**.
- The gear opens the Space's settings and is enabled only while the row reads **Stopped**,
  because settings apply when the Space next starts.
- User Spaces also carry **Rename** and **Delete**; Base has neither.

## Creating, renaming and deleting

**New Space** (the + in the top bar) asks for a name only.

![The New Space dialog on a tablet with Thesis typed as the name](images/spaces-create-light.png#only-light)
![The New Space dialog on a tablet with Thesis typed as the name](images/spaces-create-dark.png#only-dark)

- A blank name is refused; two Spaces may share one.
- Creating copies the Space's root filesystem, so it takes a moment — a progress bar shows at
  the top while it works. The new row then appears beside Base, stopped.

![The Spaces home on a tablet listing Base and Thesis, both stopped](images/spaces-list-light.png#only-light)
![The Spaces home on a tablet listing Base and Thesis, both stopped](images/spaces-list-dark.png#only-dark)

- **Rename** keeps the Space's id — only the display name changes.
- **Delete** asks for confirmation, then removes the Space and everything in it.

## Space settings

The gear opens a dialog for one Space, reached while the Space is stopped:

![The space settings dialog on a tablet: idle shutdown, thread cap, environment variables and the startup script](images/space-settings-light.png#only-light)
![The space settings dialog on a tablet: idle shutdown, thread cap, environment variables and the startup script](images/space-settings-dark.png#only-dark)

- **Idle shutdown (minutes)** — minutes of inactivity before the server stops itself; `0` means
  never.
- **Thread cap** — a ceiling some numerical libraries read; blank leaves the library default.
- **Environment variables** — set for the Space's server. `HOME` is derived from the Space's own
  directory and cannot be set.
- **Startup script** — a `sh` script run before the server on every start; a non-zero exit fails
  the start.
- **Default kernel** — what a new notebook in the Space starts on.
- **Environments** — Python environments found in the Space's workspace, each of which can be
  registered as a kernel.
- **Harnesses** — chat agents such as OpenCode and Claude that can be installed into the Space
  from here.

![The lower half of the space settings dialog on a tablet: the default kernel, environments and both chat harness rows](images/space-settings-bottom-light.png#only-light)
![The lower half of the space settings dialog on a tablet: the default kernel, environments and both chat harness rows](images/space-settings-bottom-dark.png#only-dark)

## Across restarts

No server outlives the app: swiping Jupydroid away or restarting the device leaves every row
reading **Stopped** on the next launch. A Space's files and installed packages persist — only
the running server is gone, and **Run** starts it again.

![The Spaces home on a tablet with Base running and Thesis still stopped](images/spaces-running-light.png#only-light)
![The Spaces home on a tablet with Base running and Thesis still stopped](images/spaces-running-dark.png#only-dark)

To open a running Space's notebooks, see [Getting started](getting-started.md). The
[home page](index.md) shows what the workspace looks like once inside.
