import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// gradle.properties is the single source of truth for the app's version. VERSION_NAME carries a
// trailing `# x-release-please-version` marker that release-please's generic updater rewrites;
// Java properties do not treat an inline `#` as a comment, so it is stripped here.
val versionNameProperty = (project.findProperty("VERSION_NAME") as String)
    .substringBefore("#")
    .trim()
val versionCodeProperty = (project.findProperty("VERSION_CODE") as String)
    .substringBefore("#")
    .trim()
    .toInt()

// Release signing comes from environment variables only, never a committed file or a Gradle
// property (docs/adr/0002). ANDROID_KEYSTORE_PATH is the path of the keystore a workflow decodes
// from the ANDROID_KEYSTORE_BASE64 secret; the other three are secrets of the same names. When any
// of the four is missing (a local build, a fork PR, a mis-wired workflow) no `release` signing
// config is created and the release build type is left UNSIGNED, never debug-signed: an unsigned
// APK cannot be installed and so cannot be mistaken for a release, while a debug-signed one would
// install and only fail at the next upgrade.
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyAliasPassword = System.getenv("ANDROID_KEY_ALIAS_PASSWORD")
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyAliasPassword.isNullOrBlank()

android {
    namespace = "com.derekwinters.stretch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.derekwinters.stretch"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeProperty
        versionName = versionNameProperty
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyAliasPassword

                // Every signature scheme stated rather than defaulted, as in the sibling repos:
                // AGP would drop v1 for minSdk >= 24, and a scheme set nothing states is one a
                // toolchain upgrade can change silently. The app is sideloaded, through installers
                // AGP knows nothing about.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the stable release key when its inputs are present; otherwise left
            // unsigned on purpose, never debug-signed (docs/adr/0002).
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room exports its schema here so schema changes are reviewable in PRs. Commit app/schemas/.
room {
    schemaDirectory("$projectDir/schemas")
}

// Every variant's APK is named `simple-stretch-<versionName>-<buildType>.apk`, so workflows can
// glob app/build/outputs/apk/<buildType>/*.apk. outputFileName lives only on the internal
// VariantOutputImpl, hence the cast (same approach as the sibling Android repos).
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName.set("simple-stretch-$versionNameProperty-${variant.buildType}.apk")
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
