<!--TEMPLATE FOR NEW APP INCLUSION! For updates or other types of MRs replace the text below-->

## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

**Bruce App** is a multi-platform companion app for [Bruce Firmware](https://github.com/BruceDevices/Firmware) running on ESP32 devices (e.g. M5Stack Cardputer, M5StickC). From a phone connected over USB OTG it lets you:

- **Flash firmware** to the ESP32 directly from the device (bundled `esptool` via `esptool-android`), selecting your board/family from a built-in device list.
- **Send serial commands** to the running firmware, with a catalog of built-in commands plus user-defined custom commands stored locally (SQLite).
- **Mirror the device screen** live over WebUI (connect to Bruce's WiFi AP / `bruce.local` or device IP).

Usage: connect the ESP32 via USB-C/OTG, grant the USB-serial permission, pick your device, then flash, run commands, or open screen mirror.

Screenshots are provided upstream in the Fastlane folder: `fastlane/metadata/android/en-US/images/phoneScreenshots/` (01.png–08.png).

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

Bruce Firmware users currently need a desktop + Python/esptool toolchain or a WebSerial browser to flash and control their device. Bruce App removes that: a single native Android app that flashes firmware, sends serial commands, and mirrors the screen — no PC required. No existing F-Droid app combines on-device ESP32 firmware flashing, a Bruce-specific serial command catalog with saved custom commands, and native live screen mirroring for Bruce devices.

## Checklist

### Policy

* [x] The app complies with the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy). FOSS, AGPL-3.0-only, no non-free dependencies or trackers.
* [ ] The original app author has been notified (and does not oppose the inclusion). I am the upstream maintainer (BruceDevices/App). _If not the author, paste the author's reply link here._
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) folder structure. `en-US` summary, description, icon and screenshots are present. _Note: no `changelogs/` yet — will add per-versionCode changelog files upstream._

### Docs

* [x] Read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md).
* [x] Metadata follows the best practice in [the templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/); metadata is valid.
* [x] Read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] Title: `New app: Bruce App`.
* [x] fdroiddata fork is public and the branch is not protected.
* [x] Read the [Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage).
* [x] Related fdroiddata/RFP issues are referenced below.
* [x] Only one app in this MR.

### Metadata

* [x] Metadata is at `metadata/bruce.app.yml` (matches applicationId `bruce.app`).
* [x] Valid YAML.
* [x] LF line endings.
* [x] No summary/description/changelog/images added to metadata — provided upstream via Fastlane.
* [x] Releases are tagged; auto update enabled (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`).
* [x] Issue tracker and author contact present (`IssueTracker`, `WebSite`, `AuthorName`).
* [x] `AuthorName: Bruce Devices`.
* [x] External repos as git submodules instead of srclibs. _N/A — `esptool-android` is an in-repo Gradle subproject, no external srclib/submodule used._
* [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). _Not yet enabled; release is currently unsigned in CI / signed by F-Droid. Will work on enabling RB in a follow-up; app APK is signed with F-Droid key for now._
* [x] Setup abi split if the APK is large. _N/A — no bundled native libraries; APK is small (pure Kotlin/Compose + usb-serial-for-android)._
* [x] Only the latest version (1.4 / versionCode 4) is in the metadata.
* [x] No disabled versions.
* [ ] The `commit` field is a full hash. _TODO: replace `commit: v1.4` with the full tag commit hash once v1.4 is tagged (current HEAD: `2d50889c5baaca77d88020db43eb16bd18954d80`)._

### Pipeline

* [ ] All pipelines should pass. _To be verified in CI._
* [ ] All warnings and errors in the Reports tab should be fixed or explained.
* [x] Aware F-Droid CI runners are under GitLab's FOSS program.

---------------------

Closes rfp#<RFP issue number>

/label ~"New App"
