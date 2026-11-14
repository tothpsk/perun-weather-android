# Releasing

How a new version of Perun Weather for Android is released. Only the owner of the signing key can do this.

## Steps

1. Raise `versionName` in `app/build.gradle.kts`.
2. Add a section `## [<version>]` for it to `CHANGELOG.md`. Its text becomes the release notes.
3. Merge both changes into `main`.
4. Start the Release workflow manually from the Actions tab.
5. Check the draft release `v<version>` and publish it manually.

## What the workflow does

- Reads the version from `app/build.gradle.kts`.
- Stops if a signing secret is missing, if `CHANGELOG.md` has no section for the version, or if the release `v<version>` already exists.
- Runs the localisation and translation checks, unit tests and Android lint, and builds the release APK.
- Aligns and signs the APK as `Perun-<version>.apk` and writes its SHA-256 checksum to `Perun-<version>.apk.sha256`.
- Creates a draft GitHub release `v<version>` titled "Perun Weather <version>" with both files and the notes from the changelog.

When a step fails, the workflow opens an issue describing the problem. The repository is public, so these issues are public too.

## Signing secrets

The workflow needs four repository secrets (Settings, Secrets and variables, Actions):

- `KEYSTORE_BASE64`: the keystore file encoded with base64
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The signing key must stay the same for every release, otherwise Android refuses to install an update over the existing app. Keep a backup of the keystore and the passwords outside GitHub; secrets cannot be read back. Neither the key nor the passwords belong in the repository.

## Versioning

`versionName` follows semantic versioning (MAJOR.MINOR.PATCH). `versionCode` is the number of minutes since 2026-01-01 UTC at build time, so every build is newer than the previous one. The About page shows the version with the build number, for example 1.0.3 (build 450000), and the build date.
