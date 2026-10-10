package com.jupydroid.app.markdown

import org.commonmark.node.Node

/** commonmark exposes children as a sibling chain, not a collection. */
internal fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()
