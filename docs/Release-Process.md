# Release Process

This document describes how to publish a new release of RetroDroid.

## Prerequisites

- Push access to the `master` branch
- Permission to create tags and releases on GitHub

## Steps

### 1. Update version numbers

Version numbers must be updated in **two files**:

| File | Field | Current |
|------|-------|---------|
| `Cargo.toml` (workspace root) | `[workspace.package] version` | `"0.1.0"` |
| `android/app/build.gradle.kts` | `defaultConfig.versionName` | `"0.1.0"` |
| `android/app/build.gradle.kts` | `defaultConfig.versionCode` | `1` |

`versionName` must match the workspace version in `Cargo.toml`. The tag name
must match `versionName` (without the `v` prefix). `versionCode` must be
bumped by at least 1 on every release — Android uses it to decide whether an
APK can upgrade an existing install, and it never resets to a lower value.

```bash
# Example: bumping to 0.2.0
# 1. Edit Cargo.toml
sed -i 's/^version = "0.1.0"/version = "0.2.0"/' Cargo.toml

# 2. Edit build.gradle.kts
sed -i 's/versionName = "0.1.0"/versionName = "0.2.0"/' android/app/build.gradle.kts
sed -i 's/versionCode = 1/versionCode = 2/' android/app/build.gradle.kts
```

### 2. Commit the version bump

```bash
git add Cargo.toml Cargo.lock android/app/build.gradle.kts
git commit -m "chore: bump version to 0.2.0"
git push origin master
```

### 3. Create and push a tag

The release workflow triggers on tags matching `v*` (e.g. `v0.2.0`).

```bash
git tag v0.2.0
git push origin v0.2.0
```

### 4. CI builds and creates a draft release

Pushing the tag triggers `.github/workflows/release.yml`, which:

1. **Builds the frontend library** (`retro-frontend`) for `arm64-v8a` and
   `x86_64` via `cargo ndk`
2. **Builds the bundled emulator cores** with `scripts/build-cores.sh` — all
   nine cores ship inside the APK, nothing is downloaded at runtime. If a
   core cannot be built, it is skipped (`RETRODROID_SKIP_MISSING=1`) so one
   broken core does not block the release
3. **Builds the release APKs** with `./scripts/build-apk.sh assembleRelease`
4. **Creates a draft GitHub Release** with:
   - Auto-generated release notes (PRs and commits since the previous tag)
   - A download table for the three APK variants
   - All APKs attached

### 5. Review and publish the release

1. Go to [Releases](https://github.com/AloysHF/RetroDroid/releases)
2. Find the draft release created by CI
3. Review the auto-generated changelog — edit if needed (refer to previous
   release changelogs for style)
4. Verify all expected artifacts are attached:
   - `app-universal-release.apk` — every supported ABI, the recommended
     download
   - `app-arm64-v8a-release.apk` — 64-bit ARM devices
   - `app-x86_64-release.apk` — x86_64 emulators and Chromebooks
5. Click **Publish release**

No website changes are needed: `site/` deploys automatically on every push
to `master` and links to `releases/latest`, so it picks up the new release
on its own.

## Nightly builds

Besides tagged releases, `.github/workflows/nightly-build.yml` maintains a
rolling `nightly-build` prerelease:

- Rebuilt on **every push to `master`** and on a daily cron (20:00 UTC), plus
  manual dispatch
- The previous `nightly-build` release and tag are deleted and recreated, so
  it always reflects HEAD
- Always **signed with the debug key** — users must uninstall a release build
  before installing the nightly, and vice versa
- For testing only; never reference it in user-facing announcements

## APK signing

The Gradle build signs release APKs with the release keystore only when the
`RETRODROID_KEYSTORE` environment variable points at one (with
`RETRODROID_KEYSTORE_PASSWORD`, `RETRODROID_KEY_ALIAS` and
`RETRODROID_KEY_PASSWORD` set). Otherwise it falls back to the debug key —
the nightly workflow never has a release keystore, which is why nightlies
require a clean install. CI has no secrets configured for it, so tagged
releases are debug-signed too until a release keystore is configured.

Keep the keystore out of the repository (see the "Do NOT" rules in
`AGENTS.md`).

## Troubleshooting

### CI build fails

- Check the [Actions](https://github.com/AloysHF/RetroDroid/actions) tab
  for the failed run
- The workflow pins its own SDK/NDK stack (`platforms;android-37.0`,
  `build-tools;36.0.0`, `ndk;27.2.12479018`) — a failure there is usually a
  Gradle or Rust build error, not a missing local dependency
- Run `./scripts/build-apk.sh assembleRelease` locally first; it exercises
  the same build steps

### Re-triggering a release

The release workflow only runs on tag pushes. To re-trigger:

```bash
# 1. Delete the tag locally and remotely
git tag -d v0.2.0
git push origin --delete v0.2.0

# 2. Re-push the tag (CI will re-run)
git push origin v0.2.0
```

If a draft release was already created by the failed run, delete it from the
[Releases](https://github.com/AloysHF/RetroDroid/releases) page before
re-pushing the tag, otherwise the new run may conflict with the existing
draft.

### Release artifacts missing

- Verify the tag name starts with `v` (e.g. `v0.2.0`, not `0.2.0`)
- The release workflow only triggers on tag pushes (`v*`); manually
  dispatching the workflow from the Actions tab will not create a release
- If a core was skipped during the build, the APKs are still published —
  check the build log for `skipping <core-id>` if fewer cores than expected
  are bundled

### Version mismatch between app and tag

- Ensure `Cargo.toml`, `versionName` in `android/app/build.gradle.kts`, and
  the pushed tag all carry the same version string
- `versionCode` must be strictly greater than the previous release, or
  Android will refuse the update over an existing install
