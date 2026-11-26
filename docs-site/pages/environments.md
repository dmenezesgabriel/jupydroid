# Environments

Every folder in the workspace can carry its own Python **environment** — a `.venv` made inside
the Space and registered as a kernel named for the project, so two notebooks on the same Space
can depend on different versions of one package. The **Environments** panel — the flask on the
rail — lists them beside the Space's own interpreters.

![The Environments panel on a tablet: a project folder offering Create environment, and the interpreters section below](images/environments-panel-light.png#only-light)
![The Environments panel on a tablet: a project folder offering Create environment, and the interpreters section below](images/environments-panel-dark.png#only-dark)

## Creating an environment

- Each project folder's section offers **Create environment**, which builds `<project>/.venv`
  inside it, installs `ipykernel` and registers a kernel named `<project>-.venv` — one act,
  shown in the panel while it runs.

![An environment creation in flight on a tablet: the command it runs streaming under a progress bar](images/environments-creating-light.png#only-light)
![An environment creation in flight on a tablet: the command it runs streaming under a progress bar](images/environments-creating-dark.png#only-dark)

- A create is a download, so keep Jupydroid open until it finishes; a stopped run leaves a
  half-made `.venv` to remove in a terminal.
- The same act from a terminal inside the Space is `jupydroid-env create <project>` — the
  panel's button and the command run the same script, so an environment made in the terminal
  is listed in the panel the same way.

![A created environment on a tablet: the project listing .venv with ipykernel installed, registered as a kernel](images/environments-registered-light.png#only-light)
![A created environment on a tablet: the project listing .venv with ipykernel installed, registered as a kernel](images/environments-registered-dark.png#only-dark)

## Registering a kernel

Making a kernel is an act of its own — no environment needs creating when one already exists.

- An environment the panel did not make — a `python3 -m venv` typed in the terminal, anywhere
  in the workspace — is found by the panel's scan and offers **Register as kernel**.
- The **Interpreters** section lists the Space's own pythons, and each offers the same
  **Register as kernel**; from the terminal, `jupydroid-env register <path>` does it for an
  interpreter or an environment's python alike.

## Picking a kernel

- A notebook's **kernel indicator** opens **Select Kernel**, which offers every registered
  kernel — environments list as `Python (<project>/.venv)`, registered interpreters by their
  path. The pick is recorded in the notebook, which reopens on it.

![A notebook's Select Kernel dialog on a tablet offering the environment's kernel](images/environments-kernel-picker-light.png#only-light)
![A notebook's Select Kernel dialog on a tablet offering the environment's kernel](images/environments-kernel-picker-dark.png#only-dark)

- The **Launcher** offers a notebook and a console card per registered kernel, so a new
  document can start on the environment directly — no pick needed.

![A notebook bound to an environment's kernel on a tablet: the kernel indicator naming it](images/environments-kernel-chip-light.png#only-light)
![A notebook bound to an environment's kernel on a tablet: the kernel indicator naming it](images/environments-kernel-chip-dark.png#only-dark)

- What runs there is the environment's own interpreter: `%pip install` in a cell or the
  terminal lands in its site-packages, and nothing it installs touches the Space's other
  environments or its system python.
