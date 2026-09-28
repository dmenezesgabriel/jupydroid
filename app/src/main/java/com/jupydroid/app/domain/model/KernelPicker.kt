package com.jupydroid.app.domain.model

import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelPreference
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSpecModels

/**
 * One row of the kernel picker - the `<option>` `kernelOptions` builds
 * (`packages/apputils/src/sessioncontext.tsx:1627-1833`).
 *
 * [choice] is what selecting the row asks for: a kernelspec name, a running kernel's id, or
 * **null for "No Kernel"** - `JSON.stringify(null)`, the option that means *shut the kernel
 * down* rather than let the server pick a default. The two nulls are deliberately different:
 * `KernelChoice?` at the wire means "default", and the row whose value is null means "none".
 */
data class KernelPickOption(
  val text: String,
  /** The reference's tooltip: path, session name, kernelspec and kernel id, on connect rows only. */
  val title: String? = null,
  val choice: KernelChoice?,
  /** The `<select>`'s `selected` - see [kernelPickerGroups]' marking pass. */
  val selected: Boolean = false,
)

/** An `<optgroup>` - a labelled run of options the `<select>` draws under one heading. */
data class KernelPickGroup(val label: String, val options: List<KernelPickOption>)

/**
 * `SessionContextDialogs.kernelOptions` (`sessioncontext.tsx:1627-1833`), reduced to what its
 * `<select>` actually shows.
 *
 * With a language the groups are *Start %1 Kernel* (specs matching it) → *Use No Kernel* →
 * *Start Kernel* (the rest) → *Connect to Existing %1 Kernel* → *Connect to Other Kernel*, the
 * running groups only when they have rows. Without one it is *Start Kernel* (every spec) →
 * *Use No Kernel* → *Connect to Existing Kernel* - the reference pushes that last group even
 * empty, which is how its `<select>` tells the reader no kernel is running.
 *
 * Specs are sorted by `display_name` and running kernels by their row text, the reference's own
 * `localeCompare`s. [kernelId] is `preference.id` - the kernel the session sits on - and
 * [kernelDisplayName] the resolved name the reference reads off the session context; the marking
 * pass tries them in that order, then `preference.name`, exactly as `:1805-1833` does.
 *
 * ```
 * kernelPickerGroups(specs, running, kernel.kernelPreference, session?.kernelId, displayName)
 * ```
 */
fun kernelPickerGroups(
  specs: KernelSpecModels,
  running: List<KernelSessionModel>,
  preference: KernelPreference,
  kernelId: String?,
  kernelDisplayName: String?,
): List<KernelPickGroup> {
  // `preference.language || languages[preference.name] || languages[sessions[preference.id]?.name]`
  // - the last term reads the *kernel's* spec name (`kernelName` here), the field the reference
  // reaches the same answer from.
  val languages = specs.specs.associate { it.name to it.language }
  val language = preference.language
    ?: preference.name?.let(languages::get)
    ?: kernelId?.let { id -> running.firstOrNull { it.kernelId == id }?.kernelName?.let(languages::get) }
    ?: ""
  val groups = if (language.isEmpty()) {
    groupsWithoutLanguage(specs, running)
  } else {
    groupsForLanguage(specs, running, language, languages)
  }
  return groups.markSelected(specs, preference, kernelId, kernelDisplayName)
}

/** The flat shape: every spec, No Kernel, then every running kernel - the group pushed even empty. */
private fun groupsWithoutLanguage(specs: KernelSpecModels, running: List<KernelSessionModel>) = listOf(
  KernelPickGroup("Start Kernel", specs.specs.sortedBy { it.displayName }.map { it.toOption() }),
  NO_KERNEL_GROUP,
  KernelPickGroup("Connect to Existing Kernel", running.toOptions(specs)),
)

/**
 * The language-split shape: preferred-language specs and running kernels lead their own groups,
 * the remainder stays generic - and a group with nothing to list is not pushed.
 */
private fun groupsForLanguage(
  specs: KernelSpecModels,
  running: List<KernelSessionModel>,
  language: String,
  languages: Map<String, String>,
): List<KernelPickGroup> {
  val sorted = specs.specs.sortedBy { it.displayName }
  val (preferred, other) = running.toOptions(specs).partition { option ->
    option.choice is KernelChoice.Running && languages[running.specOf(option.choice.kernelId)] == language
  }
  return buildList {
    add(KernelPickGroup("Start $language Kernel", sorted.filter { it.language == language }.map { it.toOption() }))
    add(NO_KERNEL_GROUP)
    add(KernelPickGroup("Start Kernel", sorted.filter { it.language != language }.map { it.toOption() }))
    if (preferred.isNotEmpty()) add(KernelPickGroup("Connect to Existing $language Kernel", preferred))
    if (other.isNotEmpty()) add(KernelPickGroup("Connect to Other Kernel", other))
  }
}

/**
 * What the `<select>` would answer untouched: its `selected` option, or its first - which is why
 * accepting a picker nobody marked still acts. The one case the two disagree is a single "No
 * Kernel" row, whose choice *is* null either way.
 */
fun List<KernelPickGroup>.initialPick(): KernelChoice? {
  val options = flatMap { it.options }
  return (options.firstOrNull { it.selected } ?: options.firstOrNull())?.choice
}

private val NO_KERNEL_GROUP = KernelPickGroup(
  label = "Use No Kernel",
  options = listOf(KernelPickOption(text = "No Kernel", title = "No Kernel", choice = null)),
)

private fun KernelSpec.toOption() = KernelPickOption(text = displayName, choice = KernelChoice.Spec(name))

/**
 * `optionForKernel` (`:1701-1718`): the session's name - or its path's basename - and the kernel
 * id's first UUID segment, with the `Path:/Name:/Kernel Name:/Kernel Id:` lines as the tooltip.
 */
private fun KernelSessionModel.toOption(specs: KernelSpecModels): KernelPickOption {
  val id = kernelId ?: ""
  val sessionName = name.ifEmpty { path.substringAfterLast('/') }
  val specName = specs.named(kernelName ?: "")?.displayName ?: kernelName.orEmpty()
  return KernelPickOption(
    text = "$sessionName (${id.substringBefore('-')})",
    title = "Path: $path\nName: $sessionName\nKernel Name: $specName\nKernel Id: $id",
    choice = KernelChoice.Running(id),
  )
}

/** The running kernels' rows - sessions without a kernel id have no kernel to connect to. */
private fun List<KernelSessionModel>.toOptions(specs: KernelSpecModels) =
  filter { it.kernelId != null }.map { it.toOption(specs) }.sortedBy { it.text }

/** A running option's spec name, for the language split - the row does not carry it. */
private fun List<KernelSessionModel>.specOf(kernelId: String): String? =
  firstOrNull { it.kernelId == kernelId }?.kernelName

/**
 * The marking pass (`:1805-1833`): the session's own kernel id first, then the display name the
 * bar already shows, then the persisted spec name. At most one row can match - the id check lands
 * on `Running` choices and the name checks on `Spec` ones - so marking every row answers the same
 * set as the reference's early `return`.
 */
private fun List<KernelPickGroup>.markSelected(
  specs: KernelSpecModels,
  preference: KernelPreference,
  kernelId: String?,
  kernelDisplayName: String?,
): List<KernelPickGroup> {
  if (kernelId == null && kernelDisplayName == null && preference.name == null) return this
  return map { group ->
    group.copy(
      options = group.options.map { option ->
        if (option.marked(specs, preference.name, kernelId, kernelDisplayName)) option.copy(selected = true) else option
      },
    )
  }
}

private fun KernelPickOption.marked(
  specs: KernelSpecModels,
  preferredName: String?,
  kernelId: String?,
  kernelDisplayName: String?,
): Boolean = when (val choice = choice) {
  null -> false
  is KernelChoice.Running -> kernelId != null && choice.kernelId == kernelId
  is KernelChoice.Spec -> when {
    kernelId != null -> false
    kernelDisplayName != null -> specs.named(choice.name)?.displayName == kernelDisplayName
    else -> choice.name == preferredName
  }
}
