# Versions

All versions below were verified on **2026-10-30** against the official sources listed. Where a source publishes
pre-releases as its newest entry, the newest **stable** release is used and noted.

The single source of truth for library versions is `gradle/libs.versions.toml`. This file documents what is pinned
and why.

## Toolchain

| Component | Version | Official source | Verified |
|---|---|---|---|
| Gradle (wrapper) | 9.8.2 | https://services.gradle.org/versions/current | 2026-10-30 |
| Android Gradle Plugin (AGP) | 9.5.0 | https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml | 2026-10-30 |
| Kotlin | 2.4.21 | https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml | 2026-10-30 |
| KSP | 2.3.12 | https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml | 2026-10-30 |
| JDK for builds | 21 (Temurin in CI) | https://adoptium.net/ | 2026-10-30 |
| Java bytecode target | 17 | `compileOptions` in `app/build.gradle.kts` | 2026-10-30 |

AGP 9 has built-in Kotlin support, so the `org.jetbrains.kotlin.android` plugin is intentionally not applied.

## Android platform

| Component | Version | Official source | Verified |
|---|---|---|---|
| compileSdk / targetSdk | 37 (SDK package `platforms;android-37.0`) | `sdkmanager --list` | 2026-10-30 |
| minSdk | 26 | `app/build.gradle.kts` | 2026-10-30 |
| Build Tools | 37.0.0 (`build-tools;37.0.0`) | `sdkmanager --list` | 2026-10-30 |
| Command-line tools used by CI | 16.0 (installed by `android-actions/setup-android`) | https://developer.android.com/studio#command-line-tools-only | 2026-10-30 |
| CI emulator image | `system-images;android-36;google_apis;x86_64`, profile `pixel_6` | `sdkmanager --list` | 2026-10-30 |
| Primary physical test device | Google Pixel 6 (`oriole`, 1080x2400, 420 dpi), Android 17 (API 37) | local device | 2026-10-30 |

An API 37 emulator image exists (`system-images;android-37.0;google_apis;x86_64`), but CI uses API 36, see
[CI_SETUP.md](CI_SETUP.md) for the reasoning.

## Libraries

Maven metadata sources: Google Maven `https://dl.google.com/android/maven2/<group path>/<artifact>/maven-metadata.xml`
and Maven Central `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml`.

| Component | Version | Source | Verified |
|---|---|---|---|
| Compose BOM | 2026.09.00 | Google Maven `androidx/compose/compose-bom` | 2026-10-30 |
| Compose UI (via BOM) | 1.12.1 | Google Maven `androidx/compose/ui/ui` | 2026-10-30 |
| Material 3 (via BOM) | 1.4.0 | Google Maven `androidx/compose/material3/material3` | 2026-10-30 |
| Material 3 adaptive navigation suite (via BOM) | 1.4.0 | Google Maven `androidx/compose/material3/material3-adaptive-navigation-suite` | 2026-10-30 |
| Material icons extended | 1.7.8 | Google Maven `androidx/compose/material/material-icons-extended` | 2026-10-30 |
| activity-compose | 1.13.0 | Google Maven `androidx/activity/activity-compose` | 2026-10-30 |
| core-ktx | 1.19.1 | Google Maven `androidx/core/core-ktx` | 2026-10-30 |
| core-splashscreen | 1.2.0 | Google Maven `androidx/core/core-splashscreen` | 2026-10-30 |
| Lifecycle | 2.11.0 | Google Maven `androidx/lifecycle/lifecycle-runtime-compose` | 2026-10-30 |
| Navigation Compose | 2.10.2 | Google Maven `androidx/navigation/navigation-compose` | 2026-10-30 |
| Hilt | 2.60.1 | Maven Central `com/google/dagger/hilt-android` | 2026-10-30 |
| androidx.hilt (hilt-navigation-compose, hilt-work) | 1.4.0 | Google Maven `androidx/hilt/hilt-navigation-compose` | 2026-10-30 |
| Room | 2.8.5 | Google Maven `androidx/room/room-runtime` | 2026-10-30 |
| DataStore | 1.3.0 | Google Maven `androidx/datastore/datastore-preferences` | 2026-10-30 |
| WorkManager | 2.12.0 | Google Maven `androidx/work/work-runtime-ktx` | 2026-10-30 |
| documentfile | 1.1.0 | Google Maven `androidx/documentfile/documentfile` | 2026-10-30 |
| kotlinx-coroutines | 1.11.0 | Maven Central `org/jetbrains/kotlinx/kotlinx-coroutines-core` | 2026-10-30 |
| kotlinx-serialization | 1.12.0 | Maven Central `org/jetbrains/kotlinx/kotlinx-serialization-json` | 2026-10-30 |
| OkHttp | 5.5.1 | Maven Central `com/squareup/okhttp3/okhttp` | 2026-10-30 |

### Test libraries

| Component | Version | Source | Verified |
|---|---|---|---|
| JUnit 4 | 4.13.2 | Maven Central `junit/junit` | 2026-10-30 |
| Truth | 1.4.5 | Maven Central `com/google/truth/truth` | 2026-10-30 |
| Turbine | 1.2.1 | Maven Central `app/cash/turbine/turbine` | 2026-10-30 |
| androidx.test runner | 1.7.0 | Google Maven `androidx/test/runner` | 2026-10-30 |
| androidx.test ext-junit | 1.3.0 | Google Maven `androidx/test/ext/junit` | 2026-10-30 |
| Espresso | 3.7.0 | Google Maven `androidx/test/espresso/espresso-core` | 2026-10-30 |

### Evaluated, not bundled

| Component | Latest | Source | Decision |
|---|---|---|---|
| smali / dexlib2 (`com.android.tools.smali`) | 3.0.10 | Google Maven `com/android/tools/smali/smali-dexlib2` | Evaluated, not bundled. The in-app engine is a small Kotlin parser/validator for teaching, so the full assembler is not needed. |

### Notes

- **material-icons-extended 1.7.8** is the last version ever published for this artifact. Newer Compose releases no
  longer ship it, so it is pinned on its own and is not managed by the Compose BOM. The icons used from it are
  stable vector sets, so staying on 1.7.8 carries no functional risk. If it is ever dropped, the used icons can be
  copied into the project as vector drawables.
- Google Maven currently lists pre-releases as the newest entry for several androidx artifacts (for example
  Compose UI 1.13.0-beta02, Material 3 1.5.0-beta02, activity-compose 1.14.0-alpha03, androidx.test runner
  1.8.0-alpha03). The project deliberately pins the newest **stable** releases from the table above.
- Dependency versions are not auto-bumped by lint (`GradleDependency`, `NewerVersionAvailable` are disabled in
  `app/build.gradle.kts`) so that CI results do not change because a new upstream version appeared.

## GitHub Actions

Pinned to the current major version tag. Each was verified on 2026-10-30 with
`gh api repos/<owner>/<repo>/releases/latest --jq .tag_name`.

| Action | Used as | Latest release at verification | Used in |
|---|---|---|---|
| actions/checkout | `@v7` | v7.0.1 | android-ci.yml, release.yml |
| actions/setup-java | `@v6` | v6.1.0 | android-ci.yml, release.yml |
| gradle/actions (setup-gradle) | `@v6` | v6.4.1 | android-ci.yml, release.yml |
| android-actions/setup-android | `@v4` | v4.0.4 | android-ci.yml, release.yml |
| reactivecircus/android-emulator-runner | `@v2` | v2.38.0 | android-ci.yml |
| actions/upload-artifact | `@v7` | v7.0.2 | android-ci.yml, release.yml |
| actions/cache | `@v6` | v6.1.0 | android-ci.yml |

`gradle/actions/setup-gradle` validates the Gradle wrapper automatically (documented in its
`docs/setup-gradle.md`, section "Gradle Wrapper validation"), so the separate `wrapper-validation` action is not
used.

Workflow linting: `actionlint` v1.7.12 (https://github.com/rhysd/actionlint/releases/latest, verified 2026-10-30)
with ShellCheck 0.11.0 reports no findings for both workflows.

## Documentation sources for app content

| Topic | Source |
|---|---|
| Dalvik bytecode | https://source.android.com/docs/core/runtime/dalvik-bytecode |
| Dalvik instruction formats | https://source.android.com/docs/core/runtime/instruction-formats |
| Dex file format | https://source.android.com/docs/core/runtime/dex-format |
