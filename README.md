# PairStorm v1.0 — BLE Security Lab

PairStorm is a small Android BLE advertising/scanning demo for a controlled lab.
It broadcasts a **custom PairStorm service UUID** and a configurable Bluetooth
local name. It does **not** implement Google Fast Pair and does not imitate a
commercial headset or generate Fast Pair pairing prompts.

## Build
- Open this folder in Android Studio.
- Use JDK 17 and Android SDK Platform 35.
- Allow Gradle sync, then Build > Build Bundle(s) / APK(s) > Build APK(s).
- Install the generated APK on the Redmi Note 14.

## Online build with GitHub Actions
1. Create a GitHub repository and upload the contents of this project to its root.
2. Open the repository's Actions tab and enable workflows if GitHub asks.
3. Select **Build Android APK** and click **Run workflow** (or push to `main`/`master`).
4. When the run succeeds, download **PairStorm-debug-apk** from the run's Artifacts section.

The workflow installs JDK 17, Android SDK Platform 35, and Gradle 8.9, then builds a debug APK. Debug APKs are signed with Android's debug key and are suitable for testing, not Play Store release.

## Use
1. Enable Bluetooth.
2. Grant Nearby devices permissions when prompted.
3. Enter a short beacon name (up to 18 characters).
4. Tap START BLE ADVERTISING.
5. On a second Android device, install/run PairStorm and tap START SCANNER.
6. Stop both functions after the test.

The advertiser temporarily changes the phone's Bluetooth adapter name because
Android's standard BLE advertising API obtains the advertised local name from
the adapter. PairStorm attempts to restore the previous name when advertising
stops or the app closes. Check the phone's Bluetooth name afterwards.

## Notes
- BLE advertising support and name visibility depend on the phone firmware.
- Android may reject advertising if Bluetooth is off, permissions are missing,
  or the chipset/firmware does not support the requested mode.
- The scanner currently filters for the PairStorm custom service UUID.
- This project is a real BLE beacon/scanner demonstration, not a Fast Pair
  implementation. Use authorized test devices and identifiers for protocol
  research.
