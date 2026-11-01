import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Floors under the build-only classpaths (`ktlint`, `androidLintTool`); the script carries
// the set and the reasons.
apply(from = "$rootDir/gradle/dependency-floors.gradle.kts")

// The exact version of every dependency, transitive ones included, is committed in
// `gradle.lockfile`, so a build never drifts and a scanner such as osv-scanner reads what ships.
// STRICT fails a configuration that has no lock state rather than resolving it freely
// (https://docs.gradle.org/9.3.1/userguide/dependency_locking.html#fine_tuning_dependency_locking_behaviour_with_lock_mode).
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

android {
    namespace = "com.jupydroid.app"
    // 37 because `adaptive-*:1.3.0` refuses to be consumed below it (`checkDebugAarMetadata`:
    // "requires libraries and applications that depend on it to compile against version 37 or
    // later"), which is what the adaptive-navigation3 bump actually cost - the BOM drift was the smaller half.
    //
    // **`targetSdk` deliberately stays at 36**, and nothing here changes runtime behaviour.
    // compileSdk says which APIs may be *called*; targetSdk is what opts the app into new
    // behaviour, and 36 is the number the whole exec model is arranged around
    // (ADR-0073: at 36 nothing under filesDir may be execve'd, so a Space runs under proot).
    compileSdk = 37
    defaultConfig {
        applicationId = "com.jupydroid.app"
        minSdk = 32
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // The Compose test deps were declared long before anything ran against them; this is
        // what lets androidTest actually execute (first used by CellHeaderTest).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // The whole exec model depends on this (ADR-0073):
            // only files extracted to /data/app/.../lib/<abi>/ get the apk_data_file
            // SELinux context that permits execve. Left at the modern default the
            // libs stay inside the APK, nativeLibraryDir points into it, and proot
            // - the one thing that must be execve'd - cannot run.
            useLegacyPackaging = true
        }
    }

    androidResources {
        // The rootfs asset is already zstd-compressed; recompressing it in the APK
        // costs build time and buys nothing.
        noCompress += "tzst"
    }
}

kotlin {
    jvmToolchain(17)
    // A new compiler warning fails the build: 658 had piled up unread across the source sets,
    // and a warning nobody is made to read is one nobody reads.
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
}

ktlint {
    android = false
}

// Declared before `dependencies` uses it: the Kotlin DSL evaluates the file top to bottom.
val antlrTool: Configuration by configurations.creating

dependencies {
    // `:app` is the only module that sees every extension; each sees `:extension-api` alone (ADR-0104).
    implementation(project(":extension-api"))
    implementation(project(":extensions:git"))
    implementation(project(":extensions:ai"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Process-wide lifecycle events, for the idle server stopper (ADR-0024). Neither
    // runtime-compose nor viewmodel-compose gives ON_STOP for the whole process;
    // ProcessLifecycleOwner is the only one that does.
    implementation(libs.androidx.lifecycle.process)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    // `ListDetailSceneStrategy`, which replaced a hand-written scene strategy (ADR-0047).
    // It pulls `adaptive-layout` and `adaptive-navigation` with it, and pins the whole family at
    // 1.3.0 rather than the BOM's 1.2.0 - see the catalog entry for why that is the price.
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.jsoup)
    implementation(libs.commons.compress)
    // commons-compress 1.27.1 brings commons-lang3 3.16.0, below the fix for
    // https://github.com/advisories/GHSA-j288-q9x7-2f5v; the floor is the version its own next
    // release (1.28.0) declares. A floor, not a declared need - nothing under src/main calls it.
    constraints { implementation(libs.commons.lang3) }
    // @aar: the plain jar publishes no Android ABIs; the AAR carries arm64-v8a's libzstd-jni.
    implementation("${libs.zstd.jni.get()}@aar")
    // The jar (not the AAR) so JVM unit tests get linux-x86_64's bundled zstd for archives
    // built in-test - the test fixtures' zstd bytes are produced with the same lib that
    // reads them on device.
    testImplementation(libs.zstd.jni)
    implementation(libs.antlr.runtime)
    // Tool-only: it generates the lexer at build time and is never packaged.
    antlrTool(libs.antlr.tool)
    implementation(libs.okhttp)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    // nbformat JSON persistence
    implementation(libs.kotlinx.serialization.json)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

// --- Python lexer, generated from a vendored ANTLR grammar ---------------------------
//
// Generated rather than hand-written, and vendored rather than fetched, so a build is
// reproducible offline. The grammar is antlr/grammars-v4 python3_14 (MIT); see
// tools/grammars/python/README.md for why that variant and not python3.
//
// Gradle's built-in `antlr` plugin is a Java-plugin feature and does not wire into AGP's
// source sets, so the tool is invoked directly and registered through the Variant API.

abstract class GeneratePythonLexer : JavaExec() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val grammarDir: DirectoryProperty
}

val generatePythonLexer by tasks.registering(GeneratePythonLexer::class) {
    description = "Generates the Python lexer and parser from the vendored ANTLR grammar."
    group = "build"
    grammarDir.set(layout.projectDirectory.dir("../tools/grammars/python"))

    classpath = antlrTool
    mainClass = "org.antlr.v4.Tool"
    argumentProviders.add(
        CommandLineArgumentProvider {
            val grammars = grammarDir.get()
            listOf(
                "-Dlanguage=Java",
                "-package",
                "com.jupydroid.app.syntax.python",
                "-o",
                outputDir.get().asFile.absolutePath,
                grammars.file("PythonLexer.g4").asFile.absolutePath,
                // The parser is not optional: PythonLexerBase constructs a PythonParser, so
                // the lexer does not compile without it.
                grammars.file("PythonParser.g4").asFile.absolutePath,
            )
        },
    )

    doLast {
        // The grammar's superClass ships as plain Java beside it and needs the same package
        // declaration as the generated code.
        val base = grammarDir.get().file("PythonLexerBase.java").asFile.readText()
        outputDir.get().file("PythonLexerBase.java").asFile
            .writeText("package com.jupydroid.app.syntax.python;\n\n" + base)
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generatePythonLexer,
            GeneratePythonLexer::outputDir,
        )
    }
}
