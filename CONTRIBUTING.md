# Contributing

Keep changes focused and preserve the existing module structure and UI behavior. Start with an issue describing a reproducible problem or proposed feature. Project licensing is currently unassigned; discuss licensing with the maintainer before contributing material under additional terms.

## Development

Follow [the build guide](docs/BUILDING.md), create a branch, and run:

```sh
./gradlew :app:assembleDebug testDebugUnitTest lintDebug --max-workers=2
```

Include relevant regression tests for codec, importer, persistence and cancellation changes. Describe the before/after behavior and your actual verification in the pull request.

## Infrared changes

- Cite protocol references and retain data provenance.
- Never label a submitted transmission as a confirmed appliance response.
- Keep scanning foreground-only, bounded, cancellable and restricted to selected matching profiles.
- Test with mocks first. Identify the phone and target appliance when reporting physical results; do not imply hardware coverage you did not perform.
- Do not include third-party remote databases without documented redistribution rights.

Do not commit SDK paths, credentials, private keystores, generated build directories or APK binaries. APKs belong in Releases or workflow artifacts.
