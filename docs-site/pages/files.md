# Files

The **Files** panel is the workspace's browser — every file and folder in the Space, in one
tree. It is the rail's first item, and at two panes it stays beside whatever document is open,
so the listing is never more than a glance away. Directories sort before files, and names order
naturally inside each.

![The Files panel on a tablet at the workspace root: the Workspace crumb, a reports folder, and two files](images/files-browser-light.png#only-light)
![The Files panel on a tablet at the workspace root: the Workspace crumb, a reports folder, and two files](images/files-browser-dark.png#only-dark)

## Browsing

The crumb along the panel's top names where you are — **Workspace** is the root — and each
folder's name is a segment you can tap to climb straight to it. A row opens: a folder descends,
a file opens beside the panel. Back climbs one directory before it leaves the browser.

![Inside reports/ on a tablet: the crumb reads Workspace › reports over the folder's own two rows](images/files-folder-light.png#only-light)
![Inside reports/ on a tablet: the crumb reads Workspace › reports over the folder's own two rows](images/files-folder-dark.png#only-dark)

## The New menu

The **+** on the panel's bar offers the reference's own three: **New notebook**, **New Python
file** and **New folder**. Each lands in the directory being browsed under the server's
`untitled` name, ready to be renamed from its row.

![The New menu open on a tablet listing New notebook, New Python file and New folder](images/files-new-menu-light.png#only-light)
![The New menu open on a tablet listing New notebook, New Python file and New folder](images/files-new-menu-dark.png#only-dark)

![untitled.py just created at the workspace root, listed with the folder and files already there](images/files-created-light.png#only-light)
![untitled.py just created at the workspace root, listed with the folder and files already there](images/files-created-dark.png#only-dark)

## Renaming and deleting

A long press on a row opens its menu — **Rename** and **Delete**, the touch form of the
reference's context menu. Rename keeps the extension and asks before overwriting a name
already taken; Delete asks first and says *permanently*, because there is no trash — and a
folder with something in it is refused rather than emptied.

![A row's menu open on untitled.py offering Rename and Delete](images/files-row-menu-light.png#only-light)
![A row's menu open on untitled.py offering Rename and Delete](images/files-row-menu-dark.png#only-dark)

![The Rename file dialog on a tablet with export.py typed as the new name](images/files-rename-dialog-light.png#only-light)
![The Rename file dialog on a tablet with export.py typed as the new name](images/files-rename-dialog-dark.png#only-dark)

![The delete confirmation on a tablet asking to permanently delete export.py](images/files-delete-confirm-light.png#only-light)
![The delete confirmation on a tablet asking to permanently delete export.py](images/files-delete-confirm-dark.png#only-dark)

## Recents

Opening anything — a notebook, a plain file, a folder — is what puts it in **Recent**, a
section at the workspace root that arrives collapsed. Its rows carry the whole path, because
two `summary.ipynb` in different folders are two recents, and a tap reopens the document or
browses to the folder. The section's own menu clears the list.

![The Recent section expanded on a tablet listing the folder and files that were opened](images/files-recents-light.png#only-light)
![The Recent section expanded on a tablet listing the folder and files that were opened](images/files-recents-dark.png#only-dark)

## The file editor

Anything that is not a `.ipynb` opens in the **file editor** beside the panel — text, code,
markdown, data. The bar above it is the file's ancestor crumbs, so a tap browses the panel to
that folder. Edits save themselves after a short pause, a dirty document is marked on its
tab, and a file changed on disk since it was read raises a conflict rather than silently
losing one side's work. The editor deliberately has no find bar of its own — finding text in
files is the [Search](search.md) panel's job, and a match opens the file at it.

![data.csv open in the file editor on a tablet beside the Files panel still inside reports/](images/files-editor-light.png#only-light)
![data.csv open in the file editor on a tablet beside the Files panel still inside reports/](images/files-editor-dark.png#only-dark)

## Importing

A `.ipynb` another app hands over — **Open with JupyterDroid** from a file manager — is copied
into the workspace and opened like any other document, so it lands in the listing and in
Recent. There is no import button because there is no other door: the system's share sheet is
the picker. A notebook over **15 MiB** is refused with a message rather than copied.

![An imported notebook open on a tablet beside the Files panel that already lists it](images/files-imported-open-light.png#only-light)
![An imported notebook open on a tablet beside the Files panel that already lists it](images/files-imported-open-dark.png#only-dark)

![On a tablet with the imported notebook still open: the rail's Recent section expanded, the file in the listing and recorded](images/files-imported-listed-light.png#only-light)
![On a tablet with the imported notebook still open: the rail's Recent section expanded, the file in the listing and recorded](images/files-imported-listed-dark.png#only-dark)

For the chrome the panel lives inside, see [Interface](interface.md); for what opens from
these rows, see [Notebooks](notebooks.md) and [Search](search.md); for the Spaces the files
belong to, see [Spaces](spaces.md).
