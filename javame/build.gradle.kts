// ─────────────────────────────────────────────────────────────────────────────
// Modern, WTK-free Java ME build.
//
// The traditional MIDlet toolchain (Sun WTK + Antenna) drags in 32-bit native
// binaries and an ancient JDK that no longer install cleanly on current Linux.
// This build needs none of it: every dependency comes from Maven Central and it
// runs on a normal JDK 17+.
//
// Pipeline (for each artifact):
//   1. compile  — against CLDC/MIDP (microemu) + JSR-82 (bluecove) API stubs
//   2. preverify — ProGuard's `-microedition` mode (pure Java) adds the CLDC
//                  StackMap attribute and downgrades the class version
//   3. package  — assemble the JAR and a size-synced JAD
//
// Tasks:
//   ./gradlew assemble   # builds both the app and the diagnostic probe
//   ./gradlew probe      # builds only RelayME-Probe.jar (no JSR-82 references)
// ─────────────────────────────────────────────────────────────────────────────
plugins {
    base
}

// Repositories are declared in settings.gradle.kts (dependencyResolutionManagement).

// API stubs to compile and preverify against. They are NOT bundled into the
// MIDlet (they are the device's own platform); only our classes ship.
val midpApi: Configuration by configurations.creating
// ProGuard, used purely as a CLDC preverifier here.
val proguard: Configuration by configurations.creating

dependencies {
    midpApi("org.microemu:microemu-cldc:2.0.4") // CLDC 1.1 + GCF (Connector, streams)
    midpApi("org.microemu:microemu-midp:2.0.4") // MIDP 2.0 (LCDUI, MIDlet, RMS)
    midpApi("net.sf.bluecove:bluecove:2.1.0")   // JSR-82 (javax.bluetooth) API
    proguard("net.sf.proguard:proguard-base:6.2.2")
}

val distDir = layout.buildDirectory.dir("dist")

// ProGuard arguments shared by every preverify step. `-microedition` emits the
// CLDC StackMap; `-target 1.4` downgrades to class version 48 for old KVMs
// (e.g. Esmertec Jbed on Series 30+). No shrinking/optimising/obfuscating —
// this is a pure preverifier here.
fun proguardArgs(inDir: java.io.File, outDir: java.io.File): List<String> = listOf(
    "-injars", inDir.absolutePath,
    "-outjars", outDir.absolutePath,
    "-libraryjars", midpApi.asPath,
    "-microedition",
    "-target", "1.4",
    "-dontshrink",
    "-dontoptimize",
    "-dontobfuscate",
    "-dontnote",
    "-dontwarn",
    "-keepattributes", "*",
    "-keep", "class ** { *; }",
)

// Shared MIDlet-suite attributes for generated manifests/JADs.
fun jadHeader(midlet1: String, name: String, configuration: String): List<String> = listOf(
    "MIDlet-1: $midlet1",
    "MIDlet-Name: $name",
    "MIDlet-Vendor: RelayME Project",
    "MIDlet-Version: 0.2.0",
    "MicroEdition-Configuration: $configuration",
    "MicroEdition-Profile: MIDP-2.0",
)

// ── Full application (RelayME.jar / .jad) ───────────────────────────────────

val appClasses = layout.buildDirectory.dir("app/classes")
val appPreverified = layout.buildDirectory.dir("app/preverified")

val compileMidlet by tasks.registering(JavaCompile::class) {
    description = "Compiles the MIDlet against CLDC/MIDP/JSR-82 stubs."
    setSource(fileTree("src") { include("**/*.java") })
    classpath = midpApi
    destinationDirectory.set(appClasses)
    sourceCompatibility = "8"
    targetCompatibility = "8"
    // Force StringBuilder-based concatenation, never the indy form CLDC can't run.
    options.compilerArgs.add("-XDstringConcat=inline")
    options.encoding = "UTF-8"
}

val preverify by tasks.registering(JavaExec::class) {
    description = "Preverifies the app classes for CLDC using ProGuard."
    dependsOn(compileMidlet)
    classpath = proguard
    mainClass.set("proguard.ProGuard")
    val outDir = appPreverified.get().asFile
    doFirst { outDir.deleteRecursively(); outDir.mkdirs() }
    argumentProviders.add(CommandLineArgumentProvider {
        proguardArgs(appClasses.get().asFile, outDir)
    })
}

val packageJar by tasks.registering(Jar::class) {
    description = "Packages the preverified classes into RelayME.jar."
    dependsOn(preverify)
    archiveFileName.set("RelayME.jar")
    destinationDirectory.set(distDir)
    from(appPreverified)
    manifest { from("manifest.mf") }
}

val writeJad by tasks.registering {
    description = "Writes dist/RelayME.jad with the correct MIDlet-Jar-Size."
    dependsOn(packageJar)
    doLast {
        val jar = distDir.get().file("RelayME.jar").asFile
        val lines = file("RelayME.jad").readLines().map { line ->
            when {
                line.startsWith("MIDlet-Jar-Size:") -> "MIDlet-Jar-Size: ${jar.length()}"
                line.startsWith("MIDlet-Jar-URL:") -> "MIDlet-Jar-URL: RelayME.jar"
                else -> line
            }
        }
        distDir.get().file("RelayME.jad").asFile.writeText(lines.joinToString("\n") + "\n")
        logger.lifecycle("Built ${jar.length()} byte app -> ${distDir.get().asFile}/RelayME.jar")
    }
}

// ── Standalone diagnostic probe (RelayME-Probe.jar / .jad) ──────────────────
//
// ProbeMidlet references only java.lang + LCDUI — no javax.bluetooth — so this
// JAR is guaranteed to install and run even on devices lacking JSR-82 (or that
// verify the whole suite at install time). It declares CLDC-1.0 for maximum
// compatibility. Use it to read the device's capabilities when the full app
// will not start.

val probeClasses = layout.buildDirectory.dir("probe/classes")
val probePreverified = layout.buildDirectory.dir("probe/preverified")

val compileProbe by tasks.registering(JavaCompile::class) {
    description = "Compiles only the diagnostic probe MIDlet."
    setSource(files("src/ui/ProbeMidlet.java"))
    classpath = midpApi
    destinationDirectory.set(probeClasses)
    sourceCompatibility = "8"
    targetCompatibility = "8"
    options.compilerArgs.add("-XDstringConcat=inline")
    options.encoding = "UTF-8"
}

val preverifyProbe by tasks.registering(JavaExec::class) {
    description = "Preverifies the probe class for CLDC using ProGuard."
    dependsOn(compileProbe)
    classpath = proguard
    mainClass.set("proguard.ProGuard")
    val outDir = probePreverified.get().asFile
    doFirst { outDir.deleteRecursively(); outDir.mkdirs() }
    argumentProviders.add(CommandLineArgumentProvider {
        proguardArgs(probeClasses.get().asFile, outDir)
    })
}

val packageProbeJar by tasks.registering(Jar::class) {
    description = "Packages the probe into RelayME-Probe.jar."
    dependsOn(preverifyProbe)
    archiveFileName.set("RelayME-Probe.jar")
    destinationDirectory.set(distDir)
    from(probePreverified)
    manifest {
        attributes(
            "MIDlet-1" to "RelayME Probe, , ui.ProbeMidlet",
            "MIDlet-Name" to "RelayME Probe",
            "MIDlet-Vendor" to "RelayME Project",
            "MIDlet-Version" to "0.2.0",
            "MicroEdition-Configuration" to "CLDC-1.0",
            "MicroEdition-Profile" to "MIDP-2.0",
        )
    }
}

val writeProbeJad by tasks.registering {
    description = "Writes dist/RelayME-Probe.jad with the correct size."
    dependsOn(packageProbeJar)
    doLast {
        val jar = distDir.get().file("RelayME-Probe.jar").asFile
        val lines = jadHeader("RelayME Probe, , ui.ProbeMidlet", "RelayME Probe", "CLDC-1.0") +
            listOf("MIDlet-Jar-URL: RelayME-Probe.jar", "MIDlet-Jar-Size: ${jar.length()}")
        distDir.get().file("RelayME-Probe.jad").asFile.writeText(lines.joinToString("\n") + "\n")
        logger.lifecycle("Built ${jar.length()} byte probe -> ${distDir.get().asFile}/RelayME-Probe.jar")
    }
}

val probe by tasks.registering {
    description = "Builds only the standalone diagnostic probe MIDlet."
    group = "build"
    dependsOn(writeProbeJad)
}

tasks.named("assemble") { dependsOn(writeJad, writeProbeJad) }
