// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Every plugin below resolves onto this classpath, so locking it pins AGP, KGP, detekt and ktlint's
// transitive jars in `buildscript-gradle.lockfile`
// (https://docs.gradle.org/9.3.1/userguide/dependency_locking.html#sec:activate-locking-build-script).
buildscript {
  configurations.classpath {
    resolutionStrategy.activateDependencyLocking()
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