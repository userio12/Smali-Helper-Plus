# CI setup

Two workflows live in `.github/workflows/`. `android-ci.yml` builds and tests every push and pull request without any
secrets. `release.yml` automatically publishes a signed GitHub Release (new version, tag, APK) after every push to
`main` that passes CI.
Action versions are listed in [VERSIONS.md](VERSIONS.md).

## `android-ci.yml` (Android CI)

Triggers: every push to any branch, every pull request, and manual runs (`workflow_dispatch`). There are no path
filters. Because both `push` and `pull_request` are enabled, a commit on a
branch with an open pull request is built twice (once per event); this is intentional so nothing is ever skipped
silently.

Permissions: `contents: read`. Concurrency: one run per ref; superseded **pull request** runs are cancelled,
**push** runs are never cancelled.

### Job `build`

1. Checks out the repository, sets up Temurin JDK 21.
2. `gradle/actions/setup-gradle` configures Gradle caching and validates the Gradle wrapper jar.
   Only runs on `main` write the cache; other refs read it.
3. `android-actions/setup-android` installs the SDK command-line tools plus `platforms;android-37.0` and
   `build-tools;37.0.0`.
4. Runs `./gradlew lintDebug testDebugUnitTest assembleDebug --stacktrace`. A lint error (lint has
   `abortOnError = true`), a failing unit test or a compile error fails the job.
5. `scripts/write-build-info.sh` writes `build-info.json` (version name/code, commit SHA, ref, run number and
   id, workflow, timestamp). A job summary shows it.
6. Uploads artifacts (see below).

`BuildConfig.GIT_SHA` and `BuildConfig.CI_BUILD_NUMBER` are filled from `GITHUB_SHA` and `GITHUB_RUN_NUMBER`.

### Job `instrumented-tests`

Runs in parallel with `build` (it builds the debug and test APKs itself).

1. Enables KVM on the runner (udev rule from the `android-emulator-runner` documentation) for a hardware
   accelerated emulator.
2. Same JDK, Gradle and Android SDK setup as above.
3. Restores or creates an AVD cache (key contains OS, API level, target, arch and profile). On a cache miss one
   extra emulator boot creates the snapshot that is cached.
4. `reactivecircus/android-emulator-runner` boots the emulator with `profile: pixel_6`, API 36,
   `google_apis`, `x86_64`, animations disabled, and runs `./gradlew connectedDebugAndroidTest --stacktrace`.
5. Uploads instrumented test reports (always).

There is no `continue-on-error`: if the emulator does not boot within 600 seconds or any test fails, the job fails.

### Emulator details and limitations

- **API level:** CI uses API 36 (`system-images;android-36;google_apis;x86_64`). The app targets and compiles
  against API 37 and runs on API 26 and newer. An API 37 image exists in the SDK repository
  (`system-images;android-37.0;google_apis;x86_64`), but the emulator action builds the image name from the
  `api-level` input and API 37 images are large, newer and less proven on shared runners, so they were not chosen
  for the default. You can try it by changing `EMULATOR_API_LEVEL` to `37.0` in a branch. If it works reliably, adopt it.
- **The physical Pixel 6 on Android 17 (API 37) is the reference device.** The emulator job gives automated regression
  coverage; real-device behavior (performance, dynamic color from the wallpaper, gesture navigation) is verified
  locally with `scripts/pixel6-local-test.sh` and [docs/TESTING.md](docs/TESTING.md).
- Hosted emulators are software-rendered (`swiftshader_indirect`), so timing numbers from CI are not representative.
- Hosted runners must expose `/dev/kvm`. On `ubuntu-latest` they do. If a runner type without KVM is used, the emulator
  boots extremely slowly and the job will fail on the boot timeout rather than pass silently.

### Artifacts

| Artifact | Contents | Retention |
|---|---|---|
| `Smali-Helper-Plus-Debug` | `app-debug.apk`, `build-info.json` | 30 days |
| `lint-reports` | `lint-results-debug.html`, `lint-results-debug.xml` | 14 days |
| `unit-test-reports` | `app/build/reports/tests/testDebugUnitTest/`, `app/build/test-results/` | 14 days |
| `android-test-reports` | `app/build/reports/androidTests/`, `app/build/outputs/androidTest-results/` | 14 days |

Lint, unit test and instrumented test reports are uploaded even when the job fails.

### Download the debug APK from an Actions run

1. Open the repository **Actions** tab and select the **Android CI** run (for example the latest one on `main`).
2. Scroll to **Artifacts** at the bottom of the run page and download `Smali-Helper-Plus-Debug`.
3. Unzip it and install `app-debug.apk` (`adb install -r app-debug.apk`, or open it on the phone).

With the GitHub CLI: `gh run download <run-id> -n Smali-Helper-Plus-Debug`. You need to be signed in; artifacts
from public repositories still require a GitHub login to download.

The debug APK is signed with the standard Android debug key of the runner, so it differs from a build made on another
machine. Uninstall a locally built debug version first if you see a signature conflict.

## `release.yml` (Release): automatic versioned releases

Triggers: automatically when **Android CI finishes successfully for a push to `main`** (`workflow_run`), and manual
runs from `main` (Actions > Release > Run workflow). Pushes to other branches, pull requests and failed CI runs never
publish. Permissions: `contents: write` (needed to create the tag and the release). Releases run one at a time.

### Automatic versioning

Nobody edits version numbers by hand. `app/build.gradle.kts` derives them from git:

| Value | Formula | Example |
|---|---|---|
| `versionCode` | number of commits on the released commit's history | 1, 2, 3, ... |
| `versionName` | `<major>.<minor>.<versionCode - 1>`, major/minor from [`version.properties`](version.properties) | 1.0.0, 1.0.1, 1.0.2, ... |
| Git tag / release | `v<versionName>` | `v1.0.2` |

So every push to `main` that adds commits produces a higher version (a push with 3 new commits jumps by 3). To start
a new minor or major line, edit `version.properties` (e.g. `minor=1` gives 1.1.N). `./gradlew printVersion` shows
the version locally. Both workflows check out the full history (`fetch-depth: 0`) so the count is right.

### What a release run does

1. Checks out exactly the commit that passed CI.
2. Checks that all four signing secrets exist. If not, it publishes nothing, adds a warning, and explains in the job
   summary (the run stays green so your CI badge is not red before you set up signing).
3. Computes the version; if the tag `v<versionName>` already exists (for example after a re-run), it stops.
4. Decodes the keystore (`umask 077`), builds `assembleRelease` with `SMALI_HELPER_REQUIRE_SIGNING=true` (Gradle
   fails if signing is incomplete), verifies the signature with `apksigner`, and deletes the keystore in an
   `if: always()` step.
5. Creates the GitHub Release `v<versionName>` on that commit with:
   - `Smali-Helper-Plus-v<versionName>.apk` (signed, R8-minified) and its `.sha256`,
   - `build-info.json` (version, versionCode, commit, run),
   - release notes: version table + an automatically generated changelog since the previous tag.
6. Uploads the same files plus `mapping.txt` (for de-obfuscating crash traces) as the workflow artifact
   `Smali-Helper-Plus-Release-v<versionName>` for 90 days. The mapping file is not attached to the public release.

Lint and tests are not repeated in the release job: they already passed in Android CI for the same commit, including
the Pixel 6 emulator tests. Play Store upload is not part of this pipeline.

## Re-running

- A failed or flaky run: open it and choose **Re-run failed jobs** (or **Re-run all jobs**).
- Without a new commit: **Actions > Android CI > Run workflow** and pick a branch.
- A release that failed after CI passed: **Actions > Release > Run workflow** on `main` (it skips versions that
  already have a tag).
- A stale AVD cache: delete the `avd-*` entry under **Actions > Caches** and re-run.

## Suggested branch protection

For `main` (Settings > Branches > Add rule):

- Require a pull request before merging.
- Require status checks to pass: **Lint, unit tests, assemble debug** and **Instrumented tests (Pixel 6 emulator, API 36)**.
- Require branches to be up to date before merging.
- Block force pushes and deletion.
- Restrict who can create `v*` tags (Settings > Rules > Rulesets > tag rule) to maintainers and GitHub Actions, so
  release tags only come from the release workflow.

## Local equivalents

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # any JDK 21
./gradlew lintDebug testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest                 # needs a device or emulator
scripts/check-secrets.sh                            # secrets scan
```
