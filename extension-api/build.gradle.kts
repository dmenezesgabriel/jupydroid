// The contract every extension is built against (ADR-0104): JupyterLab's `JupyterFrontEndPlugin`
// in a compile-time shape. It names Compose in its own signatures - a contributed panel is a
// `@Composable` - so Compose is `api`, not `implementation`: an extension cannot implement the
// contract without it.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.jupydroid.extension.api"
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
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    // `RenameFileDialog` is a Material `AlertDialog` the contract hands out, as JupyterLab's apputils
    // hands its plugins `InputDialog`: an extension asks for a name through the host's own dialog.
    api(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
