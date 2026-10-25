plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(libs.junit)
}

// Gradle 8 builds only buildSrc's jar for the main build, never its tests. Making the jar depend
// on the tests means every Gradle invocation that compiles buildSrc (./gradlew test, pr.yml,
// release-please.yml) runs the version-code formula tests first, and a failing one fails the
// build before any version code is computed from it (docs/adr/0003). The test task is up to date,
// and so skipped, until buildSrc changes. Needs kotlin.build.archivesTaskOutputAsFriendModule=false
// (buildSrc/gradle.properties), or the test compilation would depend on the jar: a cycle.
tasks.jar {
    dependsOn(tasks.test)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
