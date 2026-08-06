// The page side of the bridge that EditorBridge.kt speaks. Message shapes are fixed there
// and in reference/callisto/FINDINGS.md §2b; this file must not invent new ones.
import { EditorState, Compartment } from "@codemirror/state";
import { EditorView, keymap, lineNumbers, highlightActiveLineGutter } from "@codemirror/view";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import { closeBrackets, closeBracketsKeymap, autocompletion } from "@codemirror/autocomplete";
import { syntaxHighlighting, defaultHighlightStyle, indentUnit } from "@codemirror/language";
import { python } from "@codemirror/lang-python";

/** Android injects this object; without it the page is being previewed in a browser. */
function post(message) {
  if (window.JupydroidBridge) window.JupydroidBridge.postMessage(JSON.stringify(message));
}

// Pending completion requests, by id. The id comes back with the answer so a reply that
// arrives after the user has typed on can be dropped rather than applied to text that
// no longer exists.
const pendingCompletions = new Map();
let nextCompletionId = 0;

function requestCompletion(context) {
  const id = "c" + nextCompletionId++;
  const line = context.state.doc.lineAt(context.pos);
  post({
    type: "complete",
    id: id,
    source: context.state.doc.toString(),
    line: line.number - 1,
    col: context.pos - line.from,
  });
  return new Promise((resolve) => {
    pendingCompletions.set(id, { resolve: resolve, from: context.pos });
    // A kernel that never answers must not leave the menu spinning for ever.
    setTimeout(() => {
      if (pendingCompletions.delete(id)) resolve(null);
    }, 5000);
  });
}

const theme = new Compartment();

const view = new EditorView({
  parent: document.body,
  state: EditorState.create({
    doc: "",
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      history(),
      closeBrackets(),
      autocompletion({ override: [requestCompletion] }),
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
      indentUnit.of("    "),
      python(),
      EditorView.lineWrapping,
      theme.of([]),
      keymap.of([
        // Before the defaults, so Enter does not insert a newline when it means "run".
        { key: "Mod-Enter", run: () => (post({ type: "run" }), true) },
        ...closeBracketsKeymap,
        ...defaultKeymap,
        ...historyKeymap,
        indentWithTab,
      ]),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) post({ type: "edit", source: update.state.doc.toString() });
        reportHeight();
      }),
    ],
  }),
});

// No internal scroller: the page reports its height and Compose resizes the cell. Reporting
// every pixel of every keystroke would thrash layout, so a change has to be worth it.
const HEIGHT_THRESHOLD_PX = 4;
const HEIGHT_DEBOUNCE_MS = 80;
const HEIGHT_CAP_PX = 8000;
let lastReportedHeight = 0;
let heightTimer = null;

function reportHeight() {
  if (heightTimer) clearTimeout(heightTimer);
  heightTimer = setTimeout(() => {
    const height = Math.min(Math.ceil(document.documentElement.scrollHeight), HEIGHT_CAP_PX);
    if (Math.abs(height - lastReportedHeight) < HEIGHT_THRESHOLD_PX) return;
    lastReportedHeight = height;
    post({ type: "height", px: height });
  }, HEIGHT_DEBOUNCE_MS);
}

window.JupydroidEditor = {
  setSource: function (source) {
    if (source === view.state.doc.toString()) return; // echoing our own edit back would loop
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: source } });
    reportHeight();
  },

  setTheme: function (tokens) {
    for (const name of Object.keys(tokens)) {
      document.documentElement.style.setProperty("--jupydroid-" + name, tokens[name]);
    }
    view.dispatch({ effects: theme.reconfigure([]) });
    reportHeight();
  },

  completionsReady: function (answer) {
    const pending = pendingCompletions.get(answer.id);
    if (!pending) return; // late or already timed out
    pendingCompletions.delete(answer.id);
    pending.resolve({
      from: pending.from,
      options: answer.matches.map((label) => ({ label: label })),
    });
  },
};

post({ type: "ready" });
reportHeight();
