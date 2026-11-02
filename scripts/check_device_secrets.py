#!/usr/bin/env python3
"""Working Agreement (AGENTS.md): evidence proves the app, and must not describe the house it ran in.

**Why this exists.** Rule 2 asked for logcat as evidence, and 223 full-device dumps were committed
before anyone read one end to end. `adb logcat` is not this app's log - it is the whole device's:
the home Wi-Fi's BSSID and SSID (a BSSID alone geolocates a router through public wardriving
databases), Samsung's FCM push tokens, and every other app's activity. Before the first push to a
remote, history was rewritten once to take them out (ADR-0015); this gate is what keeps
the next dump from undoing that.

**Five kinds of refusal**, because each covers what the others cannot:

  1. **A dump's path**, whatever it holds - `.gitignore` already skips these, and this catches the
     `git add -f` past it.
  2. **What a dump leaks, wherever it was pasted** - a Wi-Fi network other than the emulator's own
     made-up one, and an FCM token. An excerpt quoted into a README is still the house.
  3. **A Jupyter server's credential in each carrier evidence holds it in** - the per-launch token,
     and the session cookie minted from it (TOKEN_CARRIERS). The token is random per run, so no
     literal could name it; the carrier is the stable part. `_xsrf` and `mcp-session-id` stay
     legal - one is bound to the session cookie and authenticates nothing alone, the other is not
     a credential. The offence prints the carrier and a truncated head, never the value: a live
     one must not be copied into the hook's stderr.
  4. **Local rules** - each line of `.sensitive-strings.local`, gitignored, in `git filter-repo
     --replace-text` format: a literal substring-matches, a `regex:` line compiles and searches
     (a `glob:` line is refused - nothing here honours it, and a silent pass is a leak). The same
     file drove the rewrite, so the gate refuses exactly what was scrubbed. It holds the tablet's
     serial, which cannot be a built-in rule without committing the serial. A clone without the
     file runs the built-in rules only.
  5. **The host's own `/home/<name>`** - every `maestro.log` lists it in its classpath, and it names the
     account on the machine. Built from the running account at run time (`home_rule`), never stored:
     a committed rule would be the path it removes. `scrub_device_secrets` rewrites the same class.

**Binary files are judged by path only.** A frame is pixels; reading its bytes as text finds
nothing true and costs the commit hook the whole screenshot set.

Tests in check_device_secrets_test.py, including one that runs the gate over every tracked file.
"""

from __future__ import annotations

import pathlib
import re
import sys
from typing import NamedTuple

LOCAL_LITERALS = ".sensitive-strings.local"
#: What one line of `.sensitive-strings.local` declares: a literal is substring-matched, a `regex:`
#: line is compiled and searched - the two forms `git filter-repo --replace-text` reads natively.
LocalRule = str | re.Pattern[str]

#: The account name a scrubbed path ends up under; the same replacement `read_personal_data.py` prints.
HOME_PLACEHOLDER = "user"

DUMP_PATH = re.compile(r"(^|/)([^/]*logcat[^/]*|activities|dumpsys[^/]*)\.txt$")
#: The emulator's virtual network is the same on every machine, so it identifies nobody.
EMULATOR_WIFI = {"00:13:10:85:fe:01", "AndroidWifi"}
LEAKS = (
    re.compile(r"BSSID[=:]\s*((?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})"),
    re.compile(r"\bSSID[=:]\s*\"([^\"]+)\""),
    re.compile(r"(FCM_\w*TOKEN\w*)"),
)


#: A Jupyter server's token - and the session credential minted from it - in each written form
#: the evidence reaches it in. An offence is a credential in the position that mints it, not a
#: bare long hex (the tree's pinned sha256s are legitimate). The token is minted per launch, so
#: no `.sensitive-strings.local` literal could name it; the carrier is the stable part. Group 1
#: of each pattern is the credential: this gate refuses it and scrub_device_secrets rewrites it
#: to `<token>`. {48,64} covers upstream's os.urandom(24) default
#: (usr/lib/python3.12/site-packages/jupyter_server/auth/identity.py _token_default) and our
#: TOKEN_BYTES = 32 (app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt).
class Carrier(NamedTuple):
    """One written form of the credential; the offence names it instead of printing the value."""

    name: str
    pattern: re.Pattern[str]


TOKEN_CARRIERS = (
    # argv in `ps` captures; ServerApp.token is the deprecated twin since jupyter_server 2.0.
    Carrier("--IdentityProvider.token=", re.compile(r"--(?:IdentityProvider|ServerApp)\.token=([0-9a-f]{48,64})")),
    # The DEBUG "Config changed" dump (usr/lib/python3.12/site-packages/traitlets/config/loader.py).
    Carrier(
        "'token': DeferredConfigString",
        re.compile(r"'token': DeferredConfigString\(['\"]([0-9a-f]{48,64})['\"]\)"),
    ),
    # jupyter_server/templates/page.html renders the token into every served page.
    Carrier('data-jupyter-api-token="', re.compile(r'data-jupyter-api-token="([0-9a-f]{48,64})"')),
    # The session cookie, minted by tornado's create_signed_value
    # (usr/lib/python3.12/site-packages/tornado/web.py) - quoted in a Set-Cookie line or bare in
    # a Cookie header; the pipe is the signed shape.
    Carrier("username-<host> session cookie", re.compile(r'username-[\w.-]+="?([^"\s;]*\|[^"\s;]*)')),
    # jpserver-<pid>.json, the runtime file serverapp.py writes (jpserver-mcp-<pid>.json too).
    Carrier('"token" in jpserver-<pid>.json', re.compile(r'"token": "([0-9a-f]{48,64})"')),
    # kernel-*.json connection files: a live key grants channel access the way the token does.
    Carrier('"key" in kernel-*.json', re.compile(r'"key": "([0-9a-f]{48,64})"')),
    # The GHSA-c3mw-737p-c7g2 leak shape: a token-bearing URL in an access or Referer line.
    Carrier("[?&]token= in a URL", re.compile(r"[?&]token=([0-9a-f]{48,64})")),
    # The Authorization header in both schemes, and the PersonaManager argv's 'value': 'Bearer'.
    Carrier("Authorization: token|Bearer", re.compile(r"(?:Authorization:\s*token\s+|Bearer\s+)([0-9a-f]{48,64})")),
)


def local_rules_from(replacements: str) -> list[tuple[LocalRule, str]]:
    """The (rule, replacement) pairs a `--replace-text` file declares.

    A literal substring-matches; a `regex:` line compiles and searches. `glob:` is refused:
    nothing here honours it, and a gate that read it as a literal would pass the real leak.

    ```
    local_rules_from("R9XX000==>TABLET_SERIAL\\nregex:t[0-9]+==>X\\n")
    # -> [("R9XX000", "TABLET_SERIAL"), (re.compile("t[0-9]+"), "X")]
    ```
    """
    rules: list[tuple[LocalRule, str]] = []
    for line in replacements.splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        body, _, replacement = line.partition("==>")
        if body.startswith("glob:"):
            raise ValueError(
                f"{LOCAL_LITERALS}: expected literal or 'regex:' lines like 'text==>replacement'; got {line!r}"
            )
        if body.startswith("regex:"):
            try:
                rules.append((re.compile(body.removeprefix("regex:")), replacement))
            except re.error as error:
                raise ValueError(f"{LOCAL_LITERALS}: bad regex in {line!r}: {error}") from error
        else:
            rules.append((body, replacement))
    return rules


def home_rule(home: pathlib.Path) -> tuple[re.Pattern[str], str] | None:
    """The rule that takes the host's account out of text, or None when [home] names no account.

    Built from [home] at run time, never stored: a committed rule would be the path it removes.
    Only `/home/<name>` is an account worth hiding - `/root` would match the shipped rootfs's own
    paths, and `/home/user` is the placeholder itself. The lookahead keeps `/home/ana` from
    matching `/home/anabel`.

    ```
    home_rule(pathlib.Path("/home/ana"))  # -> (re.compile(r"/home/ana(?![\\w.-])"), "/home/user")
    ```
    """
    if home.parent != pathlib.Path("/home") or home.name == HOME_PLACEHOLDER:
        return None
    return re.compile(re.escape(str(home)) + r"(?![\w.-])"), f"/home/{HOME_PLACEHOLDER}"


def _hits(rule: LocalRule, path: str, text: str) -> bool:
    """A literal is refused verbatim; a `regex:` rule on its match - in the name or the contents."""
    if isinstance(rule, str):
        return rule in path or rule in text
    return rule.search(path) is not None or rule.search(text) is not None


def offences_in(path: str, text: str, rules: list[LocalRule], home: re.Pattern[str] | None = None) -> list[str]:
    """Every reason [path] with contents [text] must not be committed; empty when it may be.

    [home] is the host's account pattern from `home_rule`; the offence names the class, never the match.

    ```
    offences_in("evidence/p1/logcat.txt", "", [])  # -> ["evidence/p1/logcat.txt: a full-device dump ..."]
    ```
    """
    offences: list[str] = []
    if DUMP_PATH.search(path):
        offences.append(f"{path}: a full-device dump; keep it local (.gitignore), quote the lines you cite")
    for leak in LEAKS:
        found = {match.group(1) for match in leak.finditer(text)} - EMULATOR_WIFI
        offences.extend(f"{path}: device data {value!r} matches {leak.pattern}" for value in sorted(found))
    for carrier in TOKEN_CARRIERS:
        found = {f"{carrier.name} ({match.group(1)[:8]}…)" for match in carrier.pattern.finditer(text)}
        offences.extend(f"{path}: a Jupyter server credential in {form}" for form in sorted(found))
    offences.extend(f"{path}: local rule from {LOCAL_LITERALS}" for rule in rules if _hits(rule, path, text))
    if home is not None and _hits(home, path, text):
        offences.append(f"{path}: the host's home path; write /home/{HOME_PLACEHOLDER} (or $HOME) instead")
    return offences


def text_of(file: pathlib.Path) -> str:
    """A file's text, or nothing for a binary one - whose path is still checked."""
    head = file.read_bytes()
    return "" if b"\x00" in head[:8192] else head.decode("utf-8", errors="replace")


def main(paths: list[str], root: pathlib.Path = pathlib.Path(), home: pathlib.Path | None = None) -> int:
    """Returns 0 when none of [paths], relative to [root], carries device data or [home]'s account.

    [home] is the running account's unless given - the one `scrub_device_secrets` removes.
    """
    local = root / LOCAL_LITERALS
    rules = [rule for rule, _ in local_rules_from(local.read_text())] if local.is_file() else []
    home_pair = home_rule(home or pathlib.Path.home())
    offences: list[str] = []
    for path in paths:
        file = root / path
        text = text_of(file) if file.is_file() else ""
        offences.extend(offences_in(path, text, rules, home_pair[0] if home_pair else None))
    for offence in offences:
        print(offence, file=sys.stderr)
    return 1 if offences else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
