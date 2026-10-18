# Building IR Hunter

## Toolchain

- JDK **17**; select it in your shell or Android Studio's Gradle JDK settings.
- Android SDK Platform **35**, Build Tools **35.0.0**, and Platform Tools.
- The checked-in Gradle **8.11.1** wrapper. Do not substitute a system Gradle installation.

Install SDK components through Android Studio's SDK Manager or:

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Set `ANDROID_HOME` to your SDK directory, or create an untracked `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

On Windows, use forward slashes in that file, for example `C:/Users/yourname/AppData/Local/Android/Sdk`.

## Compile, test and lint

```sh
./gradlew :app:assembleDebug testDebugUnitTest lintDebug --max-workers=2
```

Windows:

```powershell
.\gradlew.bat :app:assembleDebug testDebugUnitTest lintDebug --max-workers=2
```

The first build requires internet access for Gradle, Maven and Robolectric runtime downloads. This build-time requirement is separate from the installed application's offline functionality.

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Test reports: each module's `build/reports/tests/testDebugUnitTest/`.
- Lint report: `app/build/reports/lint-results-debug.html`.
- Room schemas: `core/database/schemas/` (keep schema changes under version control).

The app is `com.irhunter.app`, version `1.0.0` (1), minimum SDK 24 and target SDK 35. Automated tests use synthetic protocol values and mocked transmission; they do not prove compatibility with physical appliances.

## Packaging a test APK

```sh
mkdir -p release
cp app/build/outputs/apk/debug/app-debug.apk release/IR-Hunter-debug.apk
(cd release && sha256sum IR-Hunter-debug.apk > SHA256SUMS)
```

Use your SDK's `apksigner verify --verbose`, `aapt dump badging` and `zipalign -c -P 16 4` to verify the artifact. For device testing, install with `adb install -r` and launch `com.irhunter.app/.MainActivity`.

Debug builds are automatically signed. A newly generated debug keystore produces a different certificate: it may require uninstalling a previously signed copy, which deletes local profiles. Never commit signing keys or passwords. Production signing and store publication are not configured.

## Continuous integration

`.github/workflows/android.yml` runs on pushes to `main`, pull requests and manual dispatch. It uses JDK 17, installs the required SDK packages, runs the same build/test/lint command, and uploads the debug APK plus test/lint reports. Third-party actions are pinned to commit SHAs. Workflow artifacts are temporary; published release APKs are attached separately to GitHub Releases.

GitHub-hosted runner environment: [Ubuntu 24.04 image inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md). Gradle caching: [setup-gradle documentation](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md).
