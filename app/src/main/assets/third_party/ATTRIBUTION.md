# Sources and third-party components

No third-party device database is included in the APK. No Flipper firmware source or
fixtures were bundled. The original source comments attribute protocol timing facts
to Flipper documentation; this checkout supplied no project license or authorship
history. This work does not assign a license to that pre-existing source.

Protocol and file-format references used when inspecting and correcting the codecs:
- https://github.com/flipperdevices/flipperzero-firmware/blob/dev/documentation/file_formats/InfraredFileFormats.md
- https://github.com/flipperdevices/flipperzero-firmware/tree/dev/lib/infrared/encoder_decoder
- https://developer.android.com/reference/android/hardware/ConsumerIrManager

Imported user data retains its original text and user-entered provenance locally. No
redistribution rights are inferred from importing a file or filling in a license field.

Runtime dependencies include Kotlin / kotlinx.coroutines (JetBrains), AndroidX
Compose / Activity / Lifecycle / Navigation / Room / DataStore (Android Open Source
Project), and Dagger/Hilt (Google). These projects use Apache License 2.0. Build/test
components include Gradle, Android Gradle Plugin, KSP, JUnit and Robolectric; they are
not device datasets. Dependency versions remain in gradle/libs.versions.toml.

The Apache License 2.0 text is included in APK assets/third_party alongside these
attribution notes. See dependency project distributions for component-specific notices.
