// buildSrc is a build of its own: the root settings' repositories and catalog do not reach it.
// It holds build logic only (VersionCode.kt, docs/adr/0003). The version catalog is shared so
// JUnit's version lives in one place.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"
