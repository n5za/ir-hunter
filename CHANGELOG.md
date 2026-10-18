# Changelog

## 1.0.0-debug

Initial public test release.

- Launchable Kotlin/Compose application preserving the original six-module structure.
- Twelve device categories; brand/model search and unknown-brand/model workflows.
- Local Flipper `.ir` import, profile-derived commands and retained attribution.
- Saved profile editing, deletion and Room persistence.
- Guided scanning with progress, cancellation and manual response confirmation.
- Selected-device All OFF with confirmation, OFF preference and per-device status.
- Protocol codec repairs, emitter/frequency checks and pattern validation.
- Sixteen passing automated tests and verified debug APK packaging.

Known limits: empty initial library, no physical IR/device installation verification, built-in Consumer IR emitters only, no production signing. See [the verification report](release/RELEASE_REPORT.md).
