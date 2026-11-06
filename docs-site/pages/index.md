# JupyterDroid

An offline Jupyter notebook app for Android. It runs a real `jupyter_server` and `ipykernel` inside a
bundled Linux root filesystem on the device, on loopback, with a native Android interface. There is no
remote server.

The runtime is arm64-only. On other architectures the app installs and shows its interface against a fake
kernel that echoes a cell's source back as its output, so a cell does not run there.

The source, the release downloads and the engineering documents are in
[the repository](https://github.com/dmenezesgabriel/jupydroid).
