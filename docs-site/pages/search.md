# Search

**Search** is the rail's panel for finding text — across the whole workspace in one pass, or
inside the notebook you have open. The two are different surfaces on purpose: the panel walks
every file, and the notebook's own find bar is scoped to the document in front of you.

## The workspace panel

The panel's box searches as you type — there is no submit — and answers grouped by the file
each match was found in, with the reference's own count beside the name. A notebook's matches
name the cell they sit in, because "line 1" belongs to as many lines as the file has cells. A
tap on a match opens its file at the match.

![Workspace search on a tablet for cat: 12 results in 6 files, grouped under each file's name](images/search-results-light.png#only-light)
![Workspace search on a tablet for cat: 12 results in 6 files, grouped under each file's name](images/search-results-dark.png#only-dark)

![reports/analysis.py opened from its match on a tablet, the Search panel still beside it](images/search-open-match-light.png#only-light)
![reports/analysis.py opened from its match on a tablet, the Search panel still beside it](images/search-open-match-dark.png#only-dark)

## Options and scope

Three toggles sit on the query box itself — **Match case**, **Match whole word** and **Use
regular expression** — and each names what a tap will do, flipping its label when on. The
**⋯** beside them opens two glob boxes, **files to include** and **files to exclude**, that
narrow the walk itself: `*.py` reaches any depth, and an excluded folder is pruned whole.

![The search details open on a tablet with the files-to-include and files-to-exclude glob boxes](images/search-scope-light.png#only-light)
![The search details open on a tablet with the files-to-include and files-to-exclude glob boxes](images/search-scope-dark.png#only-dark)

![Workspace search on a tablet with *.py in files to include: 5 results in 2 files](images/search-scope-filtered-light.png#only-light)
![Workspace search on a tablet with *.py in files to include: 5 results in 2 files](images/search-scope-filtered-dark.png#only-dark)

## Replace across the workspace

The chevron beside the query box discloses the replace row: a **Replace across the workspace**
field, a **Preserve case** toggle, and **Replace all in workspace**. The question counts what
it is about to write — *Replace N occurrences across M files?* — and the answer reports the
same count in the past tense, with an **Undo** beside it. Plain files are rewritten as text and
notebooks cell by cell, so a `.ipynb` stays a notebook.

![The replace row open on a tablet: dog typed as the replacement, Preserve case on, results unchanged](images/search-replace-row-light.png#only-light)
![The replace row open on a tablet: dog typed as the replacement, Preserve case on, results unchanged](images/search-replace-row-dark.png#only-dark)

![The Replace All question on a tablet: Replace 12 occurrences across 6 files with 'dog'?](images/search-replace-confirm-light.png#only-light)
![The Replace All question on a tablet: Replace 12 occurrences across 6 files with 'dog'?](images/search-replace-confirm-dark.png#only-dark)

![The replace report on a tablet: Replaced 12 occurrences across 6 files with 'dog', with Undo](images/search-replace-report-light.png#only-light)
![The replace report on a tablet: Replaced 12 occurrences across 6 files with 'dog', with Undo](images/search-replace-report-dark.png#only-dark)

## Finding in a notebook

A notebook's own bar carries **Find in notebook**, which replaces the bar with the find one:
a box, a **Matches n/m** counter, previous and next arrows, and **Close search** to get the
bar back. Matches are counted across code, markdown and raw cells alike and lit where they sit
in an editable cell, the current one drawn bold; the counter says how many the document holds.

![The notebook find bar on a tablet: velocity typed, Matches 1/5, matches lit in the cells](images/notebook-find-bar-light.png#only-light)
![The notebook find bar on a tablet: velocity typed, Matches 1/5, matches lit in the cells](images/notebook-find-bar-dark.png#only-dark)

**Search options** on the bar holds the same three toggles as the workspace box plus two
filters the workspace walk does not have: **Search cell outputs** reaches what a cell printed,
and **Search in N selected cells** narrows the query to a marked run.

![The notebook's Search options menu open on a tablet listing the toggles and filters](images/notebook-find-options-light.png#only-light)
![The notebook's Search options menu open on a tablet listing the toggles and filters](images/notebook-find-options-dark.png#only-dark)

## Replacing in a notebook

**Show replace** inside the same menu opens a **Replace with** row with **Replace** — which
rewrites only the match the arrows stand on — and **Replace all**, which takes the rest in one
undoable command. **Preserve case** joins the options menu for as long as the row is open.

![The notebook's replace row open on a tablet: speed typed as the replacement](images/notebook-replace-light.png#only-light)
![The notebook's replace row open on a tablet: speed typed as the replacement](images/notebook-replace-dark.png#only-dark)

With outputs searched, the counter counts them too — a match the cell printed is a match the
query found.

![The notebook find bar on a tablet with Search cell outputs on: Matches 1/2 across source and output](images/notebook-search-outputs-light.png#only-light)
![The notebook find bar on a tablet with Search cell outputs on: Matches 1/2 across source and output](images/notebook-search-outputs-dark.png#only-dark)

## Plain files have no find bar

The file editor deliberately mounts none — the workspace panel is the answer for text in
files, and a match opens its file at the line or cell it was found on. What the panel cannot
do is stay inside one document: that is what the notebook's find bar is for.

For the browser the workspace files live in, see [Files](files.md); for the document the
find bar belongs to, see [Notebooks](notebooks.md); for the panel's place in the chrome, see
[Interface](interface.md).
