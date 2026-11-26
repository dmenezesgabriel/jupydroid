package com.jupydroid.app.data.space

/**
 * POSIX single-quoting: wrap in `'…'` and escape each inner `'` as `'\''` - close, escaped
 * quote, reopen - so a project named `o'brien` or `My Project` reaches the script as one word.
 */
internal fun String.shellQuoted(): String = "'${replace("'", "'\\''")}'"
