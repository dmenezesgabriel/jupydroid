import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.tasks.JavaExec
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

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
        // A release passes both, derived from its tag by scripts/release_version.py; a developer
        // build passes neither and keeps the values it always had.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("1.0")
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
val cpdTool: Configuration by configurations.creating

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
    // Tool-only, like antlrTool: the copy-paste detector behind the `cpd` task, never packaged.
    cpdTool(libs.pmd.cli)
    cpdTool(libs.pmd.kotlin)
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

// --- Licence files in the APK ----------------------------------------------------------
//
// The repository's own LICENSE, NOTICE, THIRD_PARTY.md and SOURCE_OFFER.md are copied into the
// assets at build time rather than kept as a second copy beside them, so the Licences screen shows
// what was in the tree the APK was built from. `AssetLicenceDocuments` names the same four files.

abstract class BundleLicences : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val licenceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun bundle() {
        fileSystem.sync {
            from(licenceFiles)
            into(outputDir.dir("licences"))
        }
    }
}

val bundleLicences by tasks.registering(BundleLicences::class) {
    description = "Copies the repository's licence files into the APK's assets."
    group = "build"
    licenceFiles.from(
        listOf("LICENSE", "NOTICE", "THIRD_PARTY.md", "SOURCE_OFFER.md").map { rootProject.file(it) },
    )
}

// --- Licences of the Gradle libraries in the APK ---------------------------------------
//
// Each library's POM, and the `<parent>` POMs that carry a licence the child leaves out, are copied
// into the assets, and `GradleLibraryLicences` reads them on the device. Google's oss-licenses-plugin
// was not used: it reads AGP's library-dependencies report, which AGP does not produce for a debug
// variant (the plugin writes an empty list there), and the debug APK is the one the device flows run.
// This resolves the variant's runtime classpath instead, so a debug and a release APK each list what
// they link. The resolution is a Provider chain that captures only the DependencyHandler, so the
// configuration cache can serialize it (the technique of the plugin's `OssLicensesPlugin`).

object PomResolution {
    /** The POMs of [libraries] under `libraries/`, and of every `<parent>` up their chains under `parents/`. */
    fun bundledPoms(
        dependencies: DependencyHandler,
        libraries: Collection<ModuleComponentIdentifier>,
    ): Map<String, File> {
        val libraryPoms = poms(dependencies, libraries.map { Triple(it.group, it.module, it.version) })
        val parentPoms = linkedMapOf<String, File>()
        var frontier = libraryPoms.values.mapNotNull(::parentOf)
        while (frontier.isNotEmpty()) {
            val fetched = poms(dependencies, frontier.filter { pomName(it) !in parentPoms })
            parentPoms += fetched
            frontier = fetched.values.mapNotNull(::parentOf)
        }
        return libraryPoms.mapKeys { "libraries/${it.key}" } + parentPoms.mapKeys { "parents/${it.key}" }
    }

    private fun pomName(gav: Triple<String, String, String>) = "${gav.first}_${gav.second}_${gav.third}.pom"

    private fun poms(dependencies: DependencyHandler, gavs: List<Triple<String, String, String>>): Map<String, File> {
        if (gavs.isEmpty()) return emptyMap()
        val query = dependencies.createArtifactResolutionQuery()
        gavs.forEach { (group, module, version) -> query.forModule(group, module, version) }
        val found = query.withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute().resolvedComponents.associate { component ->
                val id = component.id as ModuleComponentIdentifier
                val pom = component.getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>().firstOrNull()
                    ?: error("no POM for ${id.group}:${id.module}:${id.version}; its licence cannot be bundled")
                "${id.group}_${id.module}_${id.version}.pom" to pom.file
            }
        val missing = gavs.map(::pomName) - found.keys
        check(missing.isEmpty()) { "no POM resolved for $missing; their licences cannot be bundled" }
        return found
    }

    /** The `<parent>` coordinates a POM names, or null for a root POM. */
    private fun parentOf(pom: File): Triple<String, String, String>? {
        val project = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom).documentElement
        val parent = (0 until project.childNodes.length).map { project.childNodes.item(it) }
            .filterIsInstance<org.w3c.dom.Element>().firstOrNull { it.tagName == "parent" } ?: return null
        fun text(tag: String) = parent.getElementsByTagName(tag).item(0).textContent.trim()
        return Triple(text("groupId"), text("artifactId"), text("version"))
    }
}

abstract class BundleGradleLicences : DefaultTask() {
    // Keyed by the path under `licences/gradle/`; the values are files of the locked, sha256-verified
    // dependency cache, so a path names the same bytes every time it resolves.
    @get:Input
    abstract val pomsByBundledPath: MapProperty<String, File>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val root = outputDir.get().asFile.resolve("licences/gradle")
        root.deleteRecursively()
        pomsByBundledPath.get().forEach { (path, pom) -> pom.copyTo(root.resolve(path), overwrite = true) }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generatePythonLexer,
            GeneratePythonLexer::outputDir,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(
            bundleLicences,
            BundleLicences::outputDir,
        )
        val dependencies = project.dependencies
        val runtimeLibraries = variant.runtimeConfiguration.incoming
            .artifactView { componentFilter { it is ModuleComponentIdentifier } }.artifacts.resolvedArtifacts
        val bundleGradleLicences = tasks.register<BundleGradleLicences>(
            "bundle${variant.name.replaceFirstChar { it.uppercase() }}GradleLicences",
        ) {
            description = "Copies the POMs of the ${variant.name} runtime libraries into the APK's assets."
            group = "build"
            pomsByBundledPath.set(
                runtimeLibraries.map { artifacts ->
                    PomResolution.bundledPoms(
                        dependencies,
                        artifacts.map {
                            it.id.componentIdentifier as ModuleComponentIdentifier
                        },
                    )
                },
            )
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            bundleGradleLicences,
            BundleGradleLicences::outputDir,
        )
    }
}

// --- Duplication gate ----------------------------------------------------------------
//
// Fails on a copy of at least CPD_MINIMUM_TOKENS tokens anywhere in the four modules' Kotlin, tests
// included. 150 is where the tree measured zero in main and test sources; 100 finds the
// NotebookSaveConflict/FileSaveConflict pair, which is real and not yet merged.
val cpdMinimumTokens = 150

tasks.register<JavaExec>("cpd") {
    description = "Fails when a block of at least $cpdMinimumTokens tokens is copied within the Kotlin sources."
    group = "verification"
    classpath = cpdTool
    mainClass = "net.sourceforge.pmd.cli.PmdCli"
    val sourceDirs = listOf(".", "../extension-api", "../extensions/git", "../extensions/ai")
        .flatMap { module -> listOf("src/main", "src/test").map { "$module/$it" } }
        .map { layout.projectDirectory.dir(it).asFile }
        .filter { it.isDirectory }
    val cpdOptions = listOf("cpd", "--language", "kotlin", "--minimum-tokens", "$cpdMinimumTokens", "--format", "text")
    args(cpdOptions + sourceDirs.flatMap { listOf("--dir", it.absolutePath) })
    jvmArgs("-Xmx1g")
}
