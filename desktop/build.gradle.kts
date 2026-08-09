import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "bruce.app"
version = "1.2.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("com.fazecast:jSerialComm:2.10.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "bruce.app.MainKt"

        // Compose 1.7 ships ProGuard 7.2.2, which rejects Java 21 class files
        // (version 65 > its max 62). Shrinking saves little next to the bundled
        // JRE, and unminified avoids needing keep rules for jSerialComm/skiko.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            // Only the host's own format: Compose rejects Dmg on Linux, AppImage on macOS, etc.
            val hostOs = System.getProperty("os.name").lowercase()
            targetFormats(
                when {
                    hostOs.startsWith("mac") -> TargetFormat.Dmg
                    hostOs.startsWith("win") -> TargetFormat.Exe
                    else -> TargetFormat.AppImage
                }
            )
            packageName = "BruceApp"
            packageVersion = "1.2.0"
            description = "Bruce Firmware Flasher"
            vendor = "pr3y"
        }
    }
}
