// Bundles the CodeMirror editor into ONE self-contained assets/editor/index.html.
//
// Single file, and inlined rather than linked, because a WebView loading from
// file:///android_asset with several files needs relaxed file access; one document with no
// subresources needs none of that. Callisto ships the same shape (FINDINGS.md §2b).
import { build } from "esbuild";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, "../../app/src/main/assets/editor/index.html");

const bundled = await build({
  entryPoints: [resolve(here, "src/editor.js")],
  bundle: true,
  minify: true,
  format: "iife",
  target: ["chrome100"],
  write: false,
  legalComments: "none",
});

const js = bundled.outputFiles[0].text;
const css = readFileSync(resolve(here, "src/page.css"), "utf8");

// The script runs after the body exists, so the editor has a parent to attach to.
const html = `<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>${css}</style>
</head><body><script>${js}</script></body></html>
`;

// The bridge is two global names and a minifier is free to rename anything it thinks is
// local. If either disappears the page loads and then silently does nothing, which is a
// miserable thing to debug on a device - so fail the build here instead.
for (const global of ["JupydroidEditor", "JupydroidBridge"]) {
  if (!js.includes(global)) throw new Error(`bundle lost window.${global}; the bridge would be dead`);
}

mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, html);
console.log(`wrote ${out} (${(html.length / 1024).toFixed(0)} KB)`);
