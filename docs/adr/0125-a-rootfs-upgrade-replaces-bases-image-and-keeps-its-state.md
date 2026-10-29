# ADR-0125 — A rootfs upgrade replaces base's image and keeps the Space's own state

Status: accepted
Date: 2026-10-29

## Context

Base's rootfs comes from the APK. `BaseSpaceInstaller` extracts it again whenever
`BUNDLED_ROOTFS_VERSION` is newer than base's `.jupydroid/rootfs-version`, and until now it extracted
**over** the old tree. An entry both images carry was replaced. An entry only the old image carried
stayed.

On SM-X216B that showed up the first time a security bump went through it. The tablet's base went
from stamp 17 to 19. Afterwards `usr/lib/python3.12/site-packages` held `tornado-6.5.7.dist-info`
beside `tornado-6.6.dist-info`, and the same for jupyter_server, certifi, pip and setuptools. A cell
reading `importlib.metadata.version` printed tornado 6.5.7, jupyter_server 2.20.0 and certifi
2026.7.22, the versions the upgrade existed to remove. Any file an old version had and the new one
dropped stayed importable. A user Space is never re-extracted
([ADR-0073](0073-a-space-owns-its-rootfs-and-runs-under-proot.md)), so only base is affected.

The installer's own comment cited Winlator's `.rfs_version` for the stamp. Winlator does the other
half too. Before re-extracting, it deletes everything in its rootfs except `home`
(`winlator@fb66541 app/src/main/java/com/winlator/xenvironment/RootFSInstaller.java`, `clearRootDir`).
Termux never replaces an installed bootstrap at all (`termux-app@3df69d1 app/src/main/java/com/termux/app/TermuxInstaller.java`). A
package manager such as apk or dpkg removes an old version's files by tracking a list per package.

## Decision drivers

- After an upgrade, base runs exactly the image the APK ships.
- What the user made survives an upgrade. [ADR-0107](0107-what-the-user-made-survives-a-reinstall-through-a-filtered-auto-backup.md)
  already defines it: a Space's home and its `config.json`. A program installed into the rootfs is
  installed again.
- Deleting must never reach outside base.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Extract over the old tree (until now) | Programs installed into `usr/` survive | The old image's files survive with them, and the upgrade does not take |
| Track the extracted entries and delete only the old image's files | Programs in `usr/` survive | The image's `/lib/apk/db/installed` is replaced, so apk forgets every package the user added while its files stay: an untracked tree. Installs from before the list existed have none to read |
| **Delete every top-level entry except the Space's own state, then extract** | What runs is exactly the shipped image. This is Winlator's rule, and ADR-0107's line between the user's data and the rootfs | `opencode`, `claude-agent-acp`, `apk add`ed and `npm -g` packages in `usr/` are installed again after an update that bumps the rootfs |

## Decision

**Before a re-extract, base's top-level entries are deleted except `home/`, `pkgs/`, `.jupydroid/`
and `config.json`.** Then the image is extracted. `pkgs/` is kept because it is the user's own pip
base (`PYTHONUSERBASE`), a directory the image never carries, like the home. A link is unlinked and
never walked (`deleteTreeUnlinkingLinks`), so a link from the image into the workspace or shared
storage cannot carry the delete there. The stamp is written last, as before. An interrupted delete
or extract leaves the old stamp, and the next start repeats both.

`BUNDLED_ROOTFS_VERSION` moved to 20 with this change, so every base that took 18 or 19 by overlay is
replaced cleanly.

## Consequences

- An app update that bumps the rootfs removes programs installed into base's `usr/`. Their sign-ins
  and settings are under the home and stay. The program is installed again, as after a restore.
- `pkgs/` survives, so a `pip install --user` package is still importable. If the Python minor ever
  changes, its `lib/python3.X` simply stops being on the path.
- A future reader may want the upgrade to "keep what the user installed in `usr/`". That needs a
  per-package file list and an apk database that is merged rather than replaced. That is apk's job,
  not the installer's.
