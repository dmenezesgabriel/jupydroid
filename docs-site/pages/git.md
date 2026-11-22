# Git

**Source Control** is the rail panel the Git extension contributes: it shows the checked-out
branch and the changed files of the repository the directory the [Files](files.md) panel browses
belongs to. The read is made by the Space's own `jupyter_server`, so it needs the Space running —
the same Space whose shell the [Terminal](terminal.md) gives you. The panel is read-only: there
are no stage, commit, push or pull buttons here, because git itself is run in the terminal.

![The Source Control panel on a tablet at the workspace root reporting it is not a Git repository, with Retry](images/git-not-a-repository-light.png#only-light)
![The Source Control panel on a tablet at the workspace root reporting it is not a Git repository, with Retry](images/git-not-a-repository-dark.png#only-dark)

## Not a repository

At a directory outside every repository the panel names it — `demo is not a Git repository.` —
or says `You are not currently in a Git repository.` at the workspace root, and offers **Retry**.
Retry asks again, which is how a directory that became a repository since the last read is picked
up; a failed read shows its reason with the same **Retry**.

## The branch and the changes

Inside a repository the panel captions **Current branch** with the branch `git` has checked out,
then lists changes under the headings `git status` gives them, each with its count: **Conflicted**,
**Staged**, **Changed** and **Untracked**. A row shows the file's path and git's short-status
letter — `M` modified, `A` added, `D` deleted, `R` renamed, `C` copied — with untracked files
badged `U` and conflicts `!`; for a screen reader the row is named `<path> • <status>`
(`Modified`, `Added`, `Deleted`, `Renamed`, `Copied`, `Updated`, `Untracked` or `Conflicted`).
A file changed in both the index and the work tree can appear under both **Staged** and
**Changed**. A clean repository shows the branch alone.

![A terminal on a tablet inside demo after git status --short --branch, beside the Files panel at the workspace root](images/git-terminal-light.png#only-light)
![A terminal on a tablet inside demo after git status --short --branch, beside the Files panel at the workspace root](images/git-terminal-dark.png#only-dark)

![The Source Control panel on a tablet inside demo: Current branch feature, Staged (1) notes.py, Changed (1) README.md, Untracked (1) draft.txt](images/git-status-light.png#only-light)
![The Source Control panel on a tablet inside demo: Current branch feature, Staged (1) notes.py, Changed (1) README.md, Untracked (1) draft.txt](images/git-status-dark.png#only-dark)

The panel reads when it opens, when the directory it follows changes, and when you tap **Retry**;
it does not poll, so a change made in the terminal appears the next time the panel is read.

## Git runs in the terminal

Everything git can do — `init`, `clone`, `add`, `commit`, `checkout`, remotes, merges — is an
ordinary command in a [terminal](terminal.md) inside the same Space and workspace, so what the
terminal makes is what the panel reads. After `git checkout`, reopening the panel or moving the
Files panel reads the new branch.

For the panel's place in the chrome see [Interface](interface.md); for the directory it follows
see [Files](files.md); for the Spaces the repository belongs to see [Spaces](spaces.md).
