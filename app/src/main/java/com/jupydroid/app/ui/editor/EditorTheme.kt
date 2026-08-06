package com.jupydroid.app.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.jupydroid.app.syntax.SyntaxKind

/**
 * What each syntax category is painted with, derived from the Material scheme.
 *
 * From the scheme rather than a palette of its own, so light and dark both work without a
 * second set of colours to keep in step - and so the editor never fights the app it sits in.
 * [SyntaxKind.PLAIN] is absent on purpose: unstyled text already uses the field's own colour,
 * and restating it would just be a span that changes nothing.
 */
@Composable
@ReadOnlyComposable
fun syntaxColors(): Map<SyntaxKind, Color> = mapOf(
  SyntaxKind.KEYWORD to MaterialTheme.colorScheme.primary,
  SyntaxKind.STRING to MaterialTheme.colorScheme.tertiary,
  SyntaxKind.NUMBER to MaterialTheme.colorScheme.secondary,
  SyntaxKind.COMMENT to MaterialTheme.colorScheme.outline,
  SyntaxKind.FUNCTION to MaterialTheme.colorScheme.primary,
)

/**
 * How the bracket pair under the cursor is marked.
 *
 * A tinted box and a heavier weight rather than a colour swap: the five syntax colours are all
 * spoken for, and a sixth would have to be told apart from them at a glance while a bracket sits
 * inside coloured code anyway.
 */
@Composable
@ReadOnlyComposable
fun bracketMatchStyle(): SpanStyle = SpanStyle(
  background = MaterialTheme.colorScheme.primary.copy(alpha = MATCH_TINT),
  fontWeight = FontWeight.Bold,
)

private const val MATCH_TINT = 0.3f
