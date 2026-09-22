// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9.x: Kotlin compilation is built in (android.builtInKotlin), so the
// org.jetbrains.kotlin.android plugin is no longer applied. The compiler plugins
// below (compose, serialization) are Kotlin-compiler plugins and stay; their
// version must be >= 2.2.10, the KGP version AGP 9's built-in Kotlin requires.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
