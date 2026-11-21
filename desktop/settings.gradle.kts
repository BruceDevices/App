pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
        kotlin("jvm") version "2.0.20"
        id("org.jetbrains.compose") version "1.7.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "BruceAppDesktop"
