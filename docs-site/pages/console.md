# Console

A **console** is a conversation with a kernel, not a document: every entry you submit joins the
transcript in the order the kernel ran it, with the kernel's own count — `[1]:`, `[2]:` — and
whatever it printed or returned beneath. It is the notebook's kernel bar stripped to the
conversation, for work that belongs to no file.

There are two ways in:

- The **Launcher**'s **Python 3** card under **Console** opens a console on its **own** kernel
  session, titled **Console 1**, **Console 2** and so on — scratch work that outlives no notebook.
- A notebook's kernel **⋮** → **New Console for Notebook** opens one on the notebook's own
  kernel: the variables the notebook defined are already there, and what the console defines is
  there for the notebook's next run.

![A standalone console on a tablet: two executed entries with their prompts and outputs above the input field](images/console-open-light.png#only-light)
![A standalone console on a tablet: two executed entries with their prompts and outputs above the input field](images/console-open-dark.png#only-dark)

## Entries

- Type in the field along the bottom and tap **Run** — the button stands in for the reference's
  `Shift Enter`, which a soft keyboard does not have.
- `input()` blocks the entry on a prompt, answered in place under the entry itself — the same
  field a notebook cell gets.

![A console entry blocked on its input prompt on a tablet, the answer field under the entry](images/console-input-prompt-light.png#only-light)
![A console entry blocked on its input prompt on a tablet, the answer field under the entry](images/console-input-prompt-dark.png#only-dark)

- The **↑** and **↓** beside the field recall earlier entries — the kernel's own input history,
  not the console's: a console on a notebook's kernel recalls what the notebook ran too. While
  the field has the caret the symbol row offers the same pairs the notebook's cells do, and the
  editor settings — auto-closing brackets, indent unit — apply as they do there.

![The console's input row on a tablet with a recalled entry in the field](images/console-history-light.png#only-light)
![The console's input row on a tablet with a recalled entry in the field](images/console-history-dark.png#only-dark)

- Outputs link the way notebook outputs do: a workspace path or web address in a traceback or a
  print opens from the console.

## The console's own menu

The **⋮** at the top holds what the bar has no room for: **Interrupt Kernel**, enabled only while
an entry runs, and **Clear Console Cells**, which empties the transcript without touching the
kernel — the variables survive.

![The console's actions menu open on a tablet listing Interrupt Kernel and Clear Console Cells](images/console-actions-light.png#only-light)
![The console's actions menu open on a tablet listing Interrupt Kernel and Clear Console Cells](images/console-actions-dark.png#only-dark)

## A console on a notebook's kernel

The kernel **⋮** on a notebook's bar carries **New Console for Notebook** beside the shutdowns —
the reference's own `console:create` reach for an existing session.

![The notebook's kernel menu open on a tablet with New Console for Notebook among its rows](images/console-for-notebook-menu-light.png#only-light)
![The notebook's kernel menu open on a tablet with New Console for Notebook among its rows](images/console-for-notebook-menu-dark.png#only-dark)

The console that opens is titled with the notebook's name and shares its kernel, so a variable
the notebook's cells defined answers in the console — and one the console defines answers in the
notebook's next run.

![A console sharing the notebook's kernel on a tablet, its entry shared + 1 answered 43](images/console-for-notebook-light.png#only-light)
![A console sharing the notebook's kernel on a tablet, its entry shared + 1 answered 43](images/console-for-notebook-dark.png#only-dark)

## Sessions

A console's kernel is a session like a notebook's: while it lives it is listed under **Kernel
Sessions** in [Sessions and Tabs](interface.md#the-side-panels), and the row's ✕ ends the kernel.
Closing the console's tab leaves the session running, but the panel can only *reveal* a console
whose tab is still open — a closed console's kernel is ended from the panel, not reopened.

For the cells a console's entries resemble, see [Notebooks](notebooks.md); for the shell that
shares the Space, see [Terminal](terminal.md).
