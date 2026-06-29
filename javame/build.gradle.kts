// ─────────────────────────────────────────────────────────────────────────────
// Modern, WTK-free Java ME build.
//
// The traditional MIDlet toolchain (Sun WTK + Antenna) drags in 32-bit native
// binaries and an ancient JDK that no longer install cleanly on current Linux.
// This build needs none of it: every dependency comes from Maven Central and it
// runs on a normal JDK 17+.
//
// Pipeline:
//   1. compile  — against CLDC/MIDP (microemu) + JSR-82 (bluecove) API stubs
//   2. preverify — ProGuard's `-microedition` mode (pure Java) adds the CLDC
//                  StackMap attribute and downgrades the class version
//   3. package  — assemble dist/RelayME.jar and a size-synced RelayME.jad
//
// Usage:
//   ./gradlew assemble      # build dist/RelayME.jar + RelayME.jad
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

val classesDir = layout.buildDirectory.dir("midlet/classes")
val preverifiedDir = layout.buildDirectory.dir("midlet/preverified")
val distDir = layout.buildDirectory.dir("dist")

// 1. Compile against the stubs. Target 8 (no invokedynamic string-concat, which
//    CLDC lacks); ProGuard later downgrades the class version for old devices.
val compileMidlet by tasks.registering(JavaCompile::class) {
    description = "Compiles the MIDlet against CLDC/MIDP/JSR-82 stubs."
    setSource(fileTree("src") { include("**/*.java") })
    classpath = midpApi
    destinationDirectory.set(classesDir)
    sourceCompatibility = "8"
    targetCompatibility = "8"
    // Force StringBuilder-based concatenation, never the indy form CLDC can't run.
    options.compilerArgs.add("-XDstringConcat=inline")
    options.encoding = "UTF-8"
}

// 2. Preverify with ProGuard and downgrade to class version 48 (Java 1.4) for
//    maximum compatibility with old KVMs (e.g. Esmertec Jbed on Series 30+).
val preverify by tasks.registering(JavaExec::class) {
    description = "Preverifies the classes for CLDC using ProGuard."
    dependsOn(compileMidlet)
    classpath = proguard
    mainClass.set("proguard.ProGuard")

    val outDir = preverifiedDir.get().asFile
    doFirst {
        outDir.deleteRecursively()
        outDir.mkdirs()
    }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-injars", classesDir.get().asFile.absolutePath,
            "-outjars", outDir.absolutePath,
            "-libraryjars", midpApi.asPath,
            "-microedition",           // emit CLDC StackMap (preverification)
            "-target", "1.4",          // class version 48 for old devices
            "-dontshrink",
            "-dontoptimize",
            "-dontobfuscate",
            "-dontnote",
            "-dontwarn",
            "-keepattributes", "*",
            "-keep", "class ** { *; }",
        )
    })
}

// 3a. Package the preverified classes into the MIDlet JAR with its manifest.
val packageJar by tasks.registering(Jar::class) {
    description = "Packages the preverified classes into RelayME.jar."
    dependsOn(preverify)
    archiveFileName.set("RelayME.jar")
    destinationDirectory.set(distDir)
    from(preverifiedDir)
    manifest { from("manifest.mf") }
}

// 3b. Write the JAD with the JAR size filled in (required by MIDP installers).
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
        logger.lifecycle("Built ${jar.length()} byte MIDlet -> ${distDir.get().asFile}")
    }
}

tasks.named("assemble") { dependsOn(writeJad) }
