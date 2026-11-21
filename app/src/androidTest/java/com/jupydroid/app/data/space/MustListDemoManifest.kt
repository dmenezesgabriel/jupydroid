package com.jupydroid.app.data.space

/**
 * The must-list's thirty-six entries, grouped for the demo run: each [MustListPackage] is one
 * `%pip install` cell then one snippet cell from the library's own docs, run in a fresh kernel.
 *
 * [MustListPackage.success] is a regex the snippet cell's combined output must match - the output
 * the docs say the example produces. [MustListPackage.limitation] is set only where the demo
 * measured that the package cannot work in a Space at all (no JVM, no system library, no wheel);
 * the run then asserts the failure's own signature instead, so a different break still fails.
 */
object MustListDemoManifest {
  val GROUPS: List<MustListGroup> = listOf(
    MustListGroup("foundations", MustListDemoFoundations.PACKAGES),
    MustListGroup("visualise-and-model", MustListDemoModels.PACKAGES),
    MustListGroup("deep-learning", MustListDemoDeepLearning.PACKAGES),
    MustListGroup("deep-learning-frameworks", MustListDemoDeepLearning.FRAMEWORKS),
    MustListGroup("language-and-vision", MustListDemoLanguageVision.PACKAGES),
    MustListGroup("platform-and-serving", MustListDemoPlatform.PACKAGES),
  )
}

data class MustListGroup(val name: String, val packages: List<MustListPackage>)

data class MustListPackage(
  /** The name `%pip install` is given, e.g. `scikit-learn`, `apache-airflow`. */
  val pipName: String,
  /** The cell's source: the library's own quickstart or tutorial example. */
  val snippet: String,
  /** A regex the snippet's output must match when the package works. */
  val success: Regex,
  /**
   * A regex the output must match when the package is a measured limitation - a missing JVM or
   * system library, a wheel PyPI does not publish. Null while unmeasured.
   */
  val limitation: Regex? = null,
)
