// jupyter-ai's chat frontend, as a module against `:extension-api` (ADR-0104). Its server half is
// jupyterlab-chat's socket and jupyter-ai's personas in the Space, so this module only speaks the
// socket's JSON frames. It depends on the contract and never on `:app`: the build holds that boundary.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// The exact version of every dependency, transitive ones included, is committed in
// `gradle.lockfile`, so a build never drifts and a scanner such as osv-scanner reads what ships.
// STRICT fails a configuration that has no lock state rather than resolving it freely
// (https://docs.gradle.org/9.3.1/userguide/dependency_locking.html#fine_tuning_dependency_locking_behaviour_with_lock_mode).
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

android {
    namespace = "com.jupydroid.extensions.ai"
    // `:app`'s own numbers, for `:app`'s own reasons (app/build.gradle.kts).
    compileSdk = 37
    defaultConfig {
        minSdk = 32
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
}

ktlint {
    android = false
}

dependencies {
    implementation(project(":extension-api"))
    implementation(libs.androidx.compose.material3)
    // `Chat`, the rail's icon. Already in the APK through `:app`, so it costs nothing more.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.okhttp)
    // `AtomicFile`, the AndroidX write-then-rename the kept questions are saved with. Already in the APK
    // through `:app`, so it costs nothing more.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
