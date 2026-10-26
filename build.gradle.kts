// Plugin versions live in gradle/libs.versions.toml and are applied in :app.
// AGP 8.9.1 runs on the Gradle 8.11.1 wrapper, AGP 8.9's minimum (keep the pairing in step with the sibling Android repos).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.roborazzi) apply false
}
