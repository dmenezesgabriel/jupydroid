"""Put a Space's user site on `sys.path` before anything is installed in it.

Why
---
`site.addusersitepackages` adds the user site only when the directory already
exists (`usr/lib/python3.12/site.py`: `if ENABLE_USER_SITE and os.path.isdir(user_site)`),
and a Space's `pkgs/` starts empty: `SpaceDirs.ensure` creates `pkgs`, and the
`lib/python3.12/site-packages` under it is written by the first `pip install`. A
kernel started before that first install therefore never has the path, and the
install that creates it runs in a subprocess that does not re-run `site`, so
`import <package>` in the cell below fails until the kernel is restarted. ADR-0164
records the measurement and the other four shapes weighed.

Creating the directory is part of the repair, not a convenience: a path added to
`sys.path` while its directory is missing is cached as "no finder"
(`sys.path_importer_cache[path] = None`), so an install that creates the directory
afterwards stays invisible to that interpreter until `importlib.invalidate_caches`.
With the directory there, the first miss caches a real `FileFinder`, whose
directory-mtime check sees what pip writes later.

The path goes where `site` would have put it - before the image's own site-packages
- so a package a person upgrades into the user base shadows the shipped one, which
is the order `site.addusersitepackages` gives a machine whose directory exists.

What this costs
---------------
Every interpreter in the Space loads this, so a terminal's `python3` and `pylsp` see
the user base too. A venv created without `--system-site-packages` never processes
the image's site-packages, so it does not load the hook, and `ENABLE_USER_SITE` is
false there as well.

Loaded by `jupydroid_usersite.pth` beside this file, which `site.addsitedir` executes
for every interpreter whose `sys.path` carries the image's site-packages.
"""

from __future__ import annotations

import os
import site
import sys


def add_user_site() -> None:
    """Create the user site if it is missing and put it on `sys.path`, once.

    ```
    python3 -c "import jupydroid_usersite; jupydroid_usersite.add_user_site()"
    ```
    """
    if not site.ENABLE_USER_SITE or site.getusersitepackages() in sys.path:
        return
    user_site = site.getusersitepackages()
    os.makedirs(user_site, exist_ok=True)
    sys.path.insert(insertion_index(), user_site)


def insertion_index() -> int:
    """Where `site.addusersitepackages` would have put the user site: before the shipped packages."""
    shipped = site.getsitepackages()
    return next((index for index, entry in enumerate(sys.path) if entry in shipped), len(sys.path))
