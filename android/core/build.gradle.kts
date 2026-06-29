// Pure-JVM Kotlin library: framework-independent domain layer (no Android).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Pin Kotlin's JVM target to match Java's, so the build is consistent whatever
// JDK runs Gradle (the Kotlin plugin otherwise follows the host JDK, e.g. 21).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
