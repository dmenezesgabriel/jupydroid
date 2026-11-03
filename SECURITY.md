# Security policy

## Reporting a vulnerability

Report it privately through GitHub: open the repository's **Security** tab, choose **Report a
vulnerability**, or go straight to
<https://github.com/dmenezesgabriel/jupydroid/security/advisories/new>.

Please do not open a public issue or pull request for a vulnerability, and do not post a working
exploit anywhere public before a fix is out. This project publishes no email address for reports; the
advisory form is the only channel.

Private vulnerability reporting is available once the repository is public. Until then, a report cannot
be filed through the form above.

A report is easiest to act on when it names the Android version and device, the app commit, and the
steps that reproduce it.

## What to expect

Jupydroid has one maintainer. We aim to acknowledge a report within 7 days. We make no promise about
how long a fix takes. The advisory credits the reporter unless they ask us not to.

## Supported versions

No release exists yet. Only the `main` branch is supported.

## What is in scope

- **The Jupyter server's token.** Each launch of a Space generates a fresh random token and binds the
  server to `127.0.0.1` on a port the OS picks. Android's loopback is shared by every app that holds the
  `INTERNET` permission, so another app on the device reaching a Space's server is the threat this
  boundary exists for.
- **The MCP port.** The agent's tool server refuses any caller that does not present the server's token
  as an `Authorization: Bearer` header, and its port is picked per launch
  ([ADR-0118](docs/adr/0118-the-app-is-the-commands-toolkits-web-client-and-the-mcp-port-takes-the-servers-token.md)).
- **The sandbox and the root filesystem it runs.** Spaces run under proot on an arm64 musl root
  filesystem ([ADR-0003](docs/adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md)), with a busybox
  built without SUID support
  ([ADR-0006](docs/adr/0006-build-busybox-here-with-suid-compiled-out.md)). A way out of the sandbox,
  or into another Space, is in scope.
- **What the app exposes to other apps.** The exported launcher activity, the file provider that hands
  documents out through `content://` URIs, and the network security config, which permits cleartext
  traffic to loopback only.

## What is out of scope

- A flaw in an upstream package that the root filesystem ships. Report it to that project. If the
  version we pin is the vulnerable one and a fixed one exists, tell us: that is in scope.
- An AI agent the user installs into a Space and signs in to, and what that agent does with its own
  credentials.
- Code the user runs in a notebook. Running it is the purpose of a Space.
- A rooted or already-compromised device.
