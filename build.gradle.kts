// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Every plugin below resolves onto this classpath, so locking it pins AGP, KGP, detekt and ktlint's
// transitive jars in `buildscript-gradle.lockfile`
// (https://docs.gradle.org/9.3.1/userguide/dependency_locking.html#sec:activate-locking-build-script).
buildscript {
  configurations.classpath {
    resolutionStrategy.activateDependencyLocking()
  }
  // Floors under this classpath: plugin resolution carries these jars below an advisory's
  // fix line. gradle/dependency-floors.gradle.kts floors the same names on the module-side
  // `ktlint`/`androidLintTool` classpaths; a constraint only ever raises what a carrier
  // declares, so a plugin bump past a floor needs no edit here.
  dependencies {
    constraints {
      add("classpath", "com.google.guava:guava:33.7.2-jre").because(
        "https://github.com/advisories/GHSA-xxph-c9ww-hj94",
      )
      add("classpath", "org.apache.commons:commons-lang3:3.18.0").because(
        "https://github.com/advisories/GHSA-j288-q9x7-2f5v",
      )
      add("classpath", "org.bitbucket.b_c:jose4j:0.9.7").because(
        "https://github.com/advisories/GHSA-3677-xxcr-wjqv",
      )
      add("classpath", "org.jdom:jdom2:2.0.6.1").because(
        "https://github.com/advisories/GHSA-2363-cqg2-863c",
      )
      add("classpath", "org.bouncycastle:bcprov-jdk18on:1.86.2").because(
        "https://github.com/advisories/GHSA-9pwp-9qqc-pr26",
      )
      add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.86").because(
        "https://github.com/advisories/GHSA-wg6q-6289-32hp",
      )
      add("classpath", "org.bouncycastle:bcutil-jdk18on:1.86").because(
        "jdk18on trio; floors with bcprov",
      )
    }
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.ktlint) apply false
}