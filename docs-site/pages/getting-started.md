# Getting started

Install the published release, open the Spaces home, run the bundled **Base** Space, and run a
first cell.

You need an **arm64 Android device** running Android 12L (API level 32) or later. The runtime is
arm64-only: on other architectures the app still installs and shows its interface, but against a
fake kernel that echoes a cell's source back as its output, so a cell does not really run there.

## Install

Each [release](https://github.com/dmenezesgabriel/jupydroid/releases/latest) publishes one APK,
`jupydroid-<version>-arm64-v8a.apk`, plus `SHA256SUMS`, an SBOM and the source offer.

- **On the device:** download the APK in the browser, open it from the downloads, and allow the
  install — Android asks once to let the downloading app install apps.
- **From a computer:** with USB debugging on, `adb install jupydroid-<version>-arm64-v8a.apk`.

To check the download, fetch `SHA256SUMS` from the same release and run
`sha256sum --ignore-missing --check SHA256SUMS` in the download folder. It covers the APK and the
SBOM; `--ignore-missing` skips the SBOM when you did not download it.

## Your first notebook

1. Open **Jupydroid**. The first screen is **Spaces**, which lists the Spaces on the device —
   isolated systems, each with its own packages and Python environments. **Base** is bundled with
   the app.
2. Tap **Run** on the Base row and wait for it to read **Running**. The first start unpacks the
   bundled runtime and launches its server, so it takes a while; a progress bar shows at the top
   while it works.
3. Tap **Open**. The workspace opens on the **Files** panel.
4. Tap **New**, choose **New notebook**, and tap `Untitled.ipynb` in the list to open it. The
   notebook runs on the **Python 3** kernel and opens with one empty cell.
5. Tap the cell, type `print(6 * 7)`, then tap **Run cell** in the cell's header. `42` appears
   under the output.

## Next steps

The [home page](index.md) shows the notebook and its outputs. To build from source or contribute,
see the repository's [build-from-source guide](https://github.com/dmenezesgabriel/jupydroid#build-from-source).
