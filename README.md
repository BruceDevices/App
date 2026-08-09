# :shark: Bruce App

Multi-platform companion app for [Bruce Firmware](https://github.com/BruceDevices/Firmware) — flash firmware, run serial commands, and mirror the device screen live, all in one place.

![Bruce App](./assets/bruce_app_show.gif)

---

## Android

**Debug APK**
```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

**Release APK**
```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

---

## Desktop (Linux / macOS / Windows)

Prerequisites: JDK 21+, Python 3 + esptool (`pip install esptool`) for firmware flashing.

**Run locally**
```bash
./gradlew run
```

**Package — Linux (.AppImage)**
```bash
./gradlew packageReleaseAppImage
# output: desktop/build/compose/binaries/main-release/app-image/
```

**Package — macOS (.dmg)**
```bash
./gradlew packageReleaseDmg
# output: desktop/build/compose/binaries/main-release/dmg/
```

**Package — Windows (.exe)**
```bash
./gradlew packageReleaseExe
# output: desktop/build/compose/binaries/main-release/exe/
```

> The desktop build lives in `desktop/` as a Gradle composite build.
> You can also run tasks directly: `./gradlew --project-dir desktop run`

---

## Screen Mirror

The desktop app mirrors the Bruce device screen natively — no browser required.
Enable **WebUI** on the device (connect to Bruce's WiFi AP), then enter the host
(`bruce.local` or the device IP) in the Screen Mirror panel and press **Start Mirror**.
