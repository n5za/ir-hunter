# IR Hunter debug APK — verification report

## Project status

Inspected `the project root`. The initial checkout contained Gradle
configuration for six modules, three model files and three incomplete IR codec files.
It had no activity, UI, importer, database, saved profiles, scan engine or datasets.
The existing modules, Kotlin/Compose framework and protocol model were preserved.

Implemented a launchable dark-themed application with all twelve physical categories,
brand/model search and unknown-brand/model filters, local Flipper `.ir` import,
profile-derived buttons, saving/renaming/editing/deleting, Room persistence,
foreground guided scanning and selected-device All OFF with confirmation and status.
AC captures use manual testing because they may encode complete appliance state.

## Important fixes

- Corrected codec compilation errors, NEC42 packing/complements, Manchester bit
  handling and RC6 toggle width, SIRC repetition and decoding of the last mark.
- Added bounded pattern validation and carrier-range/emitter checks; hardware errors
  are surfaced. Separate button presses toggle RC5/RC6 state.
- Added deterministic category-filtered scan candidates, signal deduplication,
  cancellation checks and three-second scan / one-second bulk-operation delays.
- STOP and leaving the foreground cancel remaining transmissions. An already-running
  Android IR call cannot be interrupted; patterns are limited to under two seconds.
- All OFF prefers user-verified discrete OFF, then discrete OFF, then disclosed Power
  Toggle. Sent commands never imply physical confirmation that a device is OFF.
- Import checks format/version, field widths, size and timings. Unsupported/conflicting
  records are reported; original text and attribution remain in local storage.

## Build and package

Application ID / namespace: `com.irhunter.app`.
Version: `1.0.0`, code `1`. Minimum SDK 24; target/compile SDK 35.
JDK 17; Gradle wrapper 8.11.1; Android build tools 35.0.0.

Command from the project root:

```sh
./gradlew :app:assembleDebug testDebugUnitTest lintDebug --max-workers=2 --console=plain
```

APK: `release/IR-Hunter-debug.apk`.
Package assembly succeeded. Package metadata, ZIP integrity, v2 debug signature and
16 KiB native-library / 4-byte ZIP alignment checks passed. The launcher activity is
`com.irhunter.app.MainActivity`. No INTERNET permission is declared. IR hardware is
optional for installation; transmitting requires a compatible built-in emitter.

The APK and `SHA256SUMS` are attached to the [debug release](https://github.com/n5za/ir-hunter/releases/tag/v1.0.0-debug). Detailed local build logs were retained in the development workspace; they are not part of the public source tree. The complete Gradle command exited successfully.
Final APK size: 18321612 bytes.

## Test and lint results

All 16 tests passed; zero failures, errors or skipped tests:

| Suite | Passed | Coverage |
| --- | ---: | --- |
| IrCodecTest | 7 | Known NEC timings, all protocol field round trips, repeat intervals, toggle widths, invalid patterns |
| FlipperImporterTest | 3 | Little-endian fields, duplicate records, malformed and oversized input |
| CommandRunnerTest | 4 | Candidate ordering/filtering, cancellation, OFF preference, per-device failures |
| RemoteDatabaseTest | 1 | Create, edit, close/reopen restoration, delete and provenance persistence |
| MainActivityTest | 1 | Robolectric launch, no-emitter message, Saved/All OFF navigation and disabled empty selection |

Lint completed with zero errors and 54 dependency-version upgrade notices.
No lint checks or tests were disabled. The Android 12 backup-rules warning was fixed.
Test XML and lint details were reviewed locally. New CI runs upload test and lint reports as workflow artifacts.
An earlier verification run logged a non-fatal Robolectric temporary-directory
cleanup error; the final run passed without that error. No instrumented device
tests were possible. The final lint report was regenerated explicitly to replace
a stale incremental report.

## Installation and physical limits

`adb devices -l` reported no connected device. No emulator is installed in the SDK.
Installation and launch on a real device/emulator have **not** been verified.
No physical IR transmission or target-appliance response has been tested.

Install manually on a compatible Android phone:

```sh
adb install -r release/IR-Hunter-debug.apk
adb shell am start -n com.irhunter.app/.MainActivity
```

## Data, licenses and remaining limits

- No device datasets existed in the checkout or were added. The app starts with an
  empty library; import appropriate `.ir` remote files before using commands/scanning.
- Category availability does not guarantee a particular device is IR-compatible.
  USB/Bluetooth IR adapters and Bluetooth-only devices are unsupported.
- Universal mixed-device `IR library file` files are deliberately not treated as
  individual remotes. Unsupported protocols are reported during import.
- Offline behavior is implemented through app-private Room storage and no network
  permission. Airplane-mode operation on actual hardware remains untested.
- Imported source comments and user-provided source/license information are preserved.
  Import does not grant redistribution rights. No third-party code database is bundled.
- Apache-2.0 text and dependency attribution notes are packaged in assets/third_party.
  The original checkout supplied no project license or authorship history; no new
  license has been assigned to that pre-existing source.
- This is a debug-signed test build, not a production-signed store release.
