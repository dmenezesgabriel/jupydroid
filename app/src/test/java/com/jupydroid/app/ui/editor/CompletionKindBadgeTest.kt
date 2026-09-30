package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The glyph a completion row leads with. The letter is IntelliJ's completion-icon
 * convention (a monogram badge - VS Code's codicons have no Android font), and the family is
 * the colour group VS Code's `symbolIcon.*` registry actually gives each kind
 * (`editor/contrib/symbolIcons/browser/symbolIcons.ts` - only three hues are registered:
 * callables purple, class-likes amber, members blue; everything else inherits `foreground`).
 */
class CompletionKindBadgeTest {
  @Test
  fun callables_areThePurpleFamily() {
    // LSP CompletionItemKind: Method=2, Function=3, Constructor=4 - `symbolIcon.method` /
    // `.function` / `.constructor` all register the same `#B180D7`/`#652D90`.
    assertEquals(CompletionBadge("m", CompletionBadgeFamily.CALLABLE), completionBadge(2))
    assertEquals(CompletionBadge("f", CompletionBadgeFamily.CALLABLE), completionBadge(3))
    assertEquals(CompletionBadge("c", CompletionBadgeFamily.CALLABLE), completionBadge(4))
  }

  @Test
  fun classLikes_areTheAmberFamily() {
    // Class=7, Enum=13, Event=23 - `symbolIcon.class` / `.enumerator` / `.event`, the amber ids.
    assertEquals(CompletionBadge("C", CompletionBadgeFamily.CLASSLIKE), completionBadge(7))
    assertEquals(CompletionBadge("E", CompletionBadgeFamily.CLASSLIKE), completionBadge(13))
    assertEquals(CompletionBadge("e", CompletionBadgeFamily.CLASSLIKE), completionBadge(23))
  }

  @Test
  fun members_areTheBlueFamily() {
    // Field=5, Variable=6, Interface=8, EnumMember=20 - `symbolIcon.field` / `.variable` /
    // `.interface` / `.enumeratorMember`, all blue.
    assertEquals(CompletionBadge("f", CompletionBadgeFamily.MEMBER), completionBadge(5))
    assertEquals(CompletionBadge("v", CompletionBadgeFamily.MEMBER), completionBadge(6))
    assertEquals(CompletionBadge("I", CompletionBadgeFamily.MEMBER), completionBadge(8))
    assertEquals(CompletionBadge("e", CompletionBadgeFamily.MEMBER), completionBadge(20))
  }

  @Test
  fun everythingElse_inheritsForeground() {
    // Kinds the registry leaves at `foreground` still get a monogram, just no hue.
    assertEquals(CompletionBadge("M", CompletionBadgeFamily.PLAIN), completionBadge(9))
    assertEquals(CompletionBadge("p", CompletionBadgeFamily.PLAIN), completionBadge(10))
    assertEquals(CompletionBadge("k", CompletionBadgeFamily.PLAIN), completionBadge(14))
    assertEquals(CompletionBadge("a", CompletionBadgeFamily.PLAIN), completionBadge(1))
  }

  /**
   * `kind` is `Int?` rather than an enum, because pylsp's jedi mapping can send any number the
   * spec leaves room for - an unmapped one earns no badge rather than a guessed one, and a
   * kernel answer (null) earns none because the kernel never claimed one.
   */
  @Test
  fun anUnmappedOrAbsentKind_earnsNoBadge() {
    assertNull(completionBadge(null))
    assertNull(completionBadge(0))
    assertNull(completionBadge(26))
    assertNull(completionBadge(-1))
  }
}
