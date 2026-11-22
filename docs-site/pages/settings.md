# Settings

**Settings** is the app-wide preferences screen — every editor toggle, the theme and launcher
icon choices, the Diagnostics section and the Licences documents, in one scrolling list. It is
reached from the gear at the foot of the side rail beside two panes, and from **More →
Settings** at one pane; it stacks over whatever is open rather than replacing it, and closing it
returns to the document.

## Appearance

**Theme** is three always-visible choices — **System**, **Light** and **Dark** — so the current
pick is never hidden behind a menu. System follows the device's own light/dark setting; Light
and Dark pin the app regardless of it.

**App icon** offers the two launcher icons as previews — **Blue orbit** and **Black orbit** —
the worn one ringed and badged with a check. The choice changes the icon the launcher shows;
there is no restart and no settings file behind it, only the platform's own component state.

![Settings on a tablet: the Theme segments on System, the two app-icon previews with Blue orbit
selected, and the editor toggles beneath](images/settings-appearance-light.png#only-light)
![Settings on a tablet: the Theme segments on System, the two app-icon previews with Blue orbit
selected, and the editor toggles beneath](images/settings-appearance-dark.png#only-dark)

## The editor rows

Every toggle in the list is app-wide and survives a restart. **Show line numbers** puts the
gutter beside a cell's or a file's lines; **Symbol row** is the touch keyboard's own row of
brackets and operators; **Code font size** steps the cell editor's type between 8 and 32 sp;
**Wrap code lines** decides whether long source scrolls sideways or wraps — markdown and raw
cells always wrap, as the reference hardwires them to; **Auto-closing brackets** types the
closing half of a pair itself; **Indentation unit** picks what one level of indent is —
**Tab**, **1**, **2**, **4** or **8** spaces — and feeds the symbol row's Tab key and the
formatting options a range-format request carries; **Code completion** turns the kernel's
completions on or off; and **Kernel history access** lets a cell being edited step back through
what the kernel has already run — off by default, as it is in the reference.

## Documents, autosave and the kernel

**Autosave documents** writes edits back without asking; with it off, a close is what asks.
**Autosave interval** sets how often the periodic sweep writes a document that is still dirty —
5 to 600 seconds in steps of 5, 120 by default — and stays visible with autosave off, so an
interval set ahead of the toggle is still the one it starts with. **Shut down kernel on
close** ends a notebook's kernel when the notebook closes rather than only dropping the socket —
off by default, because a kernel is expensive to boot and the two-pane sweep closes documents
the user only glanced at.

## The table of contents

**Heading numbers** numbers the headings a notebook's outline lists; while it is on,
**Number top-level headings** appears beneath it to choose whether level-one headings are
numbered too — the row exists only while there are numbers for it to change. **Output
headings** lists the headings a cell *printed* beside the ones its neighbours wrote.

![Settings scrolled to its lower rows on a tablet: Autosave documents and interval, Shut down
kernel on close, and the three table-of-contents rows with Number top-level headings
visible](images/settings-toc-light.png#only-light)
![Settings scrolled to its lower rows on a tablet: Autosave documents and interval, Shut down
kernel on close, and the three table-of-contents rows with Number top-level headings
visible](images/settings-toc-dark.png#only-dark)

## Diagnostics and Licences

**Diagnostics** is the app's own record of what went wrong — its entries, their size, and the
two things a person can do with them. It is covered in full on the
[Troubleshooting](troubleshooting.md) page.

![Settings at its foot on a tablet: the Diagnostics section reading 0 entries · 0 B with Share
log and Clear logs, and the Licences row](images/settings-diagnostics-light.png#only-light)
![Settings at its foot on a tablet: the Diagnostics section reading 0 entries · 0 B with Share
log and Clear logs, and the Licences row](images/settings-diagnostics-dark.png#only-dark)

**Licences** opens the licence documents the APK ships — the app's own licence and notices, the
Gradle libraries it links, the third-party software list and the source offer — each readable
in place.

![The Licences screen on a tablet listing the five bundled documents](images/settings-licences-light.png#only-light)
![The Licences screen on a tablet listing the five bundled documents](images/settings-licences-dark.png#only-dark)

For the chrome the settings configure, see [Interface](interface.md); for what a wrong-looking
run means, see [Troubleshooting](troubleshooting.md).
