# Changelog

## 1.1.0-debug

- Added 121 offline remote profiles containing 2,586 usable commands.
- Removed arbitrary per-category catalog limits.
- Pinned the source snapshot and included its CC0 attribution and licensing boundary.
- Added one-time, idempotent first-launch seeding; deleting a bundled profile no longer restores it on every launch.
- Added a catalog integrity test that parses every packaged `.ir` file with the production importer.

The upstream snapshot contains three more newly licensed `.ir` paths with invalid headers; they are excluded. Older Flipper-IRDB content is not bundled because upstream explicitly excludes pre-boundary commits from its current CC0 grant.

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
