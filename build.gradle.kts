// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.7.20" apply false
    alias(libs.plugins.android.library) apply false
}

tasks.register("clean", Delete::class) {
    delete(getLayout().getBuildDirectory())
}

// Desktop convenience tasks — delegate to the composite-included desktop build
tasks.register("run") {
    dependsOn(gradle.includedBuild("desktop").task(":run"))
}
tasks.register("packageReleaseAppImage") {
    dependsOn(gradle.includedBuild("desktop").task(":packageReleaseAppImage"))
}
tasks.register("packageReleaseDmg") {
    dependsOn(gradle.includedBuild("desktop").task(":packageReleaseDmg"))
}
tasks.register("packageReleaseExe") {
    dependsOn(gradle.includedBuild("desktop").task(":packageReleaseExe"))
}