# Terminal

A **terminal** is a real shell inside the Space — the same `ash` the Space's scripts get, with
its own files, tools and installed packages. It is where git, compilers and anything that has no
notebook cell run, and it is the same shell [Spaces](spaces.md) isolates per Space.

## Opening one

The **Launcher**'s **Terminal** card opens one; at one pane, where there is no room for the
launcher's cards, **More → New terminal** on the bottom bar reaches the same place. Each opens as
a tab — **Terminal 1**, **Terminal 2**, and so on — or takes the name the shell gives itself.

![A fresh terminal on a tablet: the shell's prompt, the extra-keys row along the bottom](images/terminal-open-light.png#only-light)
![A fresh terminal on a tablet: the shell's prompt, the extra-keys row along the bottom](images/terminal-open-dark.png#only-dark)

The shell draws faithfully: colours, full-screen programs like `vi`, scrollback you drag
through, and a grid that follows the window when it rotates or the keyboard opens and closes.

## The extra-keys row

The row along the bottom is what a soft keyboard cannot send:

- **Esc** and **Tab** send their bytes outright.
- **Ctrl** and **Alt** *latch*: a tap arms the next keystroke typed on the keyboard, and a
  long-press locks the modifier until tapped again — `Ctrl` then `c` is the interrupt `^C`.
- The arrows, **Home**, **End**, **PgUp** and **PgDn** send the keys a hardware keyboard would.

## Selecting and the clipboard

Press and hold a word to select it — drag the handles at its ends to grow or move the selection
— and the platform's floating toolbar offers **Copy** and **Paste**. The **⋮** beside the top bar
reaches the same paste while nothing is selected.

![A terminal selection on a tablet: sel42word highlighted with a handle under each end](images/terminal-selection-light.png#only-light)
![A terminal selection on a tablet: sel42word highlighted with a handle under each end](images/terminal-selection-dark.png#only-dark)

## Links

A web address the shell prints — or one a program marks with an `OSC 8` escape — is a link: tap
it and **Open Link?** asks before the browser opens it, because the target is whatever the
program wrote. Only `http` and `https` count.

![The Open Link ask on a tablet naming the address a tap found](images/terminal-link-light.png#only-light)
![The Open Link ask on a tablet naming the address a tap found](images/terminal-link-dark.png#only-dark)

## The actions menu

The **⋮** at the top holds the terminal's own settings: **Increase** and **Decrease Terminal
Font Size**, each disabled at its bound, and **Paste**. The font size is kept across launches.

![The terminal's actions menu open on a tablet listing the font-size steps and Paste](images/terminal-actions-light.png#only-light)
![The terminal's actions menu open on a tablet listing the font-size steps and Paste](images/terminal-actions-dark.png#only-dark)

![The same terminal after two font-size increases on a tablet, its text drawn larger](images/terminal-font-size-light.png#only-light)
![The same terminal after two font-size increases on a tablet, its text drawn larger](images/terminal-font-size-dark.png#only-dark)

## The shell outlives its tab

Closing a terminal's tab does not kill the shell: it stays listed under **Terminals** in
[Sessions and Tabs](interface.md#the-side-panels), where a tap reattaches with its scrollback —
and it survives the app going to the background, answering the moment you come back. Several
terminals can run at once, one tab each.

For running a notebook's cells, see [Notebooks](notebooks.md); for what the panel that lists
these shells is, see [Interface](interface.md).
