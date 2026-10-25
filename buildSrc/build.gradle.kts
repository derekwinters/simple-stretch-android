plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(libs.junit)
}

// Gradle 8 builds only buildSrc's jar for the main build, never its tests, so `./gradlew test`
// does not run them. Run them with `./gradlew -p buildSrc test`; pr.yml does (docs/adr/0003).

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
