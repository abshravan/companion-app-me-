// Root build script. Plugins are declared here without applying them so each
// module opts in to exactly what it needs (Clean Architecture: pure-JVM
// modules never pull in the Android plugin).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
