import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.jupydroid.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jupydroid.app"
        minSdk = 32
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // The Compose test deps have been declared since Step 1 with nothing to run; this is
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
            // The whole exec model depends on this (docs/PLAN.md, "Exec model"):
            // only files extracted to /data/app/.../lib/<abi>/ get the apk_data_file
            // SELinux context that permits execve. Left at the modern default the
            // libs stay inside the APK, nativeLibraryDir points into it, and nothing
            // is executable.
            useLegacyPackaging = true
        }
    }

    androidResources {
        // The rootfs asset is already gzipped; recompressing it in the APK costs
        // build time and buys nothing.
        noCompress += "tgz"
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

// Declared before `dependencies` uses it: the Kotlin DSL evaluates the file top to bottom.
val antlrTool: Configuration by configurations.creating

dependencies {
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

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.commonmark)
    implementation(libs.commons.compress)
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

    // nbformat JSON persistence (Step 3)
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
