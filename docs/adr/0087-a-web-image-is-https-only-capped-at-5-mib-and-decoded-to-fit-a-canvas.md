# ADR-0087 — A web image is https only, capped at 5 MiB, and decoded to fit a canvas

Status: accepted
Date: 2026-10-11

## Context

A markdown image can name a web address, and the app now fetches it
(`RemoteImageReader`, `app/src/main/java/com/jupydroid/app/domain/port/RemoteImageReader.kt`). A
browser decides three things for JupyterLab that this app must decide for itself: which schemes it
fetches, how many bytes it takes, and what it does with a picture too large to draw.

What the references do:

- **JupyterLab** leaves all three to the browser. Lab is served over `http://localhost`, so no
  mixed-content rule stops an `http:` image (jupyterlab@7f0e690 `packages/rendermime/src/renderers.ts`).
- **VS Code's notebook webview** allows `img-src … https: http: data:`
  (vscode@3f3380c6 `src/vs/workbench/contrib/notebook/browser/view/renderers/backLayerWebView.ts`).
  **Its markdown preview** is stricter by default: the `Strict` level allows `https: data:` only, and
  insecure content is an opt-in (`extensions/markdown-language-features/src/preview/documentRenderer.ts`,
  `security.ts`).
- **Jewel** hands the source to Coil3, which decodes at `Size.ORIGINAL` behind a 20 MB memory cache
  (intellij-community@406a27a8 `platform/jewel/markdown/extensions/images/.../Coil3ImageRendererExtension.kt`).
  It runs on a desktop JVM, where there is no cleartext policy and no canvas limit.

None of the nine references caps the bytes it fetches.

What the platform does, on the device this app ships to:

- **Cleartext.** Since API 28 cleartext is refused unless a network security config allows it, and a
  config allows it per *domain* or app-wide in `base-config`, never per caller
  (https://developer.android.com/privacy-and-security/security-config). This app's config allows
  loopback only, so that nothing else in it speaks HTTP to the network
  (`app/src/main/res/xml/network_security_config.xml`).
- **The canvas limit.** A hardware canvas throws `"Canvas: trying to draw too large(…bytes) bitmap."`
  when a bitmap's `getByteCount()` exceeds `RecordingCanvas.MAX_BITMAP_SIZE`, a hidden constant equal
  to `max(ro.hwui.max_texture_allocation_size, default)`. The default is 100 MiB on API 32
  (`android12L-release`, this app's `minSdk`) and 150 MiB on API 36 (`android16-release`)
  (AOSP `graphics/java/android/graphics/RecordingCanvas.java`, `getPanelFrameSize`). The size of
  the PNG does not bound it: a few kilobytes can decode to more.

Measured on SM-X216B (API 36, `ro.hwui.max_texture_allocation_size` unset, so 150 MiB;
`dalvik.vm.heapgrowthlimit` 256m):

- A 6400² PNG drawn whole threw `Canvas: trying to draw too large(163840000bytes) bitmap.` in
  `RichTextImageTest.anImageTooLargeToDrawWholeDrawsShrunkToTheColumn` before the decode below
  existed.
- upload.wikimedia.org answered 403 to OkHttp's default `okhttp/5.4.0` User-Agent and to an empty
  one, and 200 to one naming its client, `Jupydroid/1.0` (its policy:
  https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy).
- A 5 156 055-byte, 5200 × 6800 JPEG from Commons drew across the column within 3 s of scrolling
  into view (one hierarchy dump a second). Relaunched in airplane mode, it drew again from the
  cache, as did a 3 230-byte PNG served with `max-age=300`. A 5 545 153-byte JPEG drew its alt.

## Decision drivers

- The loopback-only network config is a security property. A feature may not widen it as a side
  effect.
- No notebook may crash the app by naming an image.
- Where no reference sets a number, the number comes from a system that already serves notebook
  images.

## Options considered

| Axis | Option | Pro | Con |
|---|---|---|---|
| Scheme | **https only** | Keeps the network config as it is; matches the platform default and VS Code's default markdown security | An `http:` image draws its alt where Lab draws it |
| Scheme | http and https | Lab's and the VS Code notebook's behaviour | Requires app-wide cleartext, since the config cannot scope it to one caller |
| Byte cap | **5 MiB** | GitHub's image proxy, camo, which serves the images in notebooks rendered on GitHub, defaults `CAMO_LENGTH_LIMIT` to 5 242 880 | A larger image draws its alt |
| Byte cap | none | Every reference's behaviour | One notebook can make the app download any amount |
| Too large to draw | **decode at a power-of-two sample size under 100 MiB** | It still draws, at the column's width, which is all ADR-0086 gives it | A little resolution is lost on an image that only fits the column anyway |
| Too large to draw | draw the alt | Less code | Every reference draws the image |

## Decision

**A web image is fetched only from `https:`** (`isWebImageSource`,
`app/src/main/java/com/jupydroid/app/richtext/RichLinkDestination.kt`). Every other scheme goes to
the next resolver out, which answers nothing, so its alt draws.

**A body over 5 MiB is nothing** (`RemoteImageReader.MAX_IMAGE_BYTES`). `OkHttpRemoteImageReader`
refuses a declared `Content-Length` over the cap before reading the body, and reads a body that
declares no length only to one byte past the cap. Requests go through an OkHttp HTTP cache of ten
images at the cap, in `cacheDir/remote-images`, and name the app before the library:
`Jupydroid/<versionName> okhttp/<version>`.

**Every markdown image is decoded to fit the canvas floor** (`imageSampleSize`,
`app/src/main/java/com/jupydroid/app/ui/editor/ImageSampleSize.kt`). The header is read first, and
`inSampleSize` is doubled until `width × height × 8` divided by its square is at most 100 MiB. The 8
is the worst case: a PNG with 16 bits per channel decodes to `RGBA_F16`. 100 MiB is the floor on
every release from `minSdk` on.

## Consequences

An `http:` image, one over 5 MiB, and a host that is down with nothing cached all draw their alt,
like a missing attachment. A web image seen once draws offline for as long as its server said it
stays fresh.

What a future reader will be tempted to "fix":

- **Adding `http` to `isWebImageSource`.** The platform refuses the request anyway. Making it succeed
  means `cleartextTrafficPermitted` in `base-config`, which turns cleartext on for the whole app.
- **Reading the real `MAX_BITMAP_SIZE`.** It is `@hide`. Reflection on it is what a hidden-API
  blocklist exists to break.
- **Dropping the User-Agent.** Wikimedia refuses OkHttp's default with a 403.

Not covered:

- Image outputs decode through `OutputImage`, which has no such guard. A `display(Image(...))` that
  large still throws at draw.
