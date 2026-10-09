plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so no separate kotlin-android plugin is needed.
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
