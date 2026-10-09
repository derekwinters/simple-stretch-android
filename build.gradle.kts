// Plugin versions live in gradle/libs.versions.toml and are applied in :app.
// AGP 8.7.3 matches the Gradle 8.11.1 wrapper (same pairing as the sibling Android repos).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}
