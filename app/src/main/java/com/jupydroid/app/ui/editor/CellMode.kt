package com.jupydroid.app.ui.editor

/**
 * Which of the notebook's two modes the keyboard - and, on a phone, the on-screen controls - are
 * currently talking to.
 *
 * Not our idea, and not a small one: both references build their entire interaction model on it.
 * IntelliJ declares `enum NotebookEditorMode { EDIT, COMMAND }` and publishes changes on a
 * message bus so every renderer can react
 * (`notebooks/notebook-ui/.../command/mode/NotebookEditorMode.kt`); JupyterLab scopes almost all
 * of its 56 default key bindings to `.jp-mod-commandMode`, which is what lets `D,D` mean "delete
 * this cell" in one mode and "type two d's" in the other.
 *
 * **COMMAND is the resting state**, as it is in both - a notebook you have opened but not typed
 * into is not being edited.
 *
 * On a phone the mode earns its keep differently. There is no `Escape` and usually no hardware
 * keyboard, so the modes are what decide *which controls are on screen*: the cell action bar and
 * the notebook bar in COMMAND, the symbol row above the keyboard in EDIT.
 */
enum class CellMode {
  COMMAND,
  EDIT,
}
