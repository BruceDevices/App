pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Bruce App"
include(":app")
include(":esptool-android")

// Desktop app — included as a composite build so the root gradlew covers everything.
// Build with: ./gradlew --project-dir desktop <task>
// e.g.: ./gradlew --project-dir desktop run
//        ./gradlew --project-dir desktop packageReleaseDeb
includeBuild("desktop")
