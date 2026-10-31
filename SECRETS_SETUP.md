# Secrets setup

Short version: **debug builds, lint, unit tests and the emulator tests need no secrets at all.** The four signing
secrets below are only used by `.github/workflows/release.yml`, which publishes a signed GitHub Release automatically
after every successful push to `main`. Without them nothing is published (CI still builds and tests everything).

## Secrets used by this project

| Secret name (exact) | Required? | Used for |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | For automatic releases | The release keystore file, base64 encoded on one line |
| `ANDROID_KEYSTORE_PASSWORD` | For automatic releases | Password of the keystore |
| `ANDROID_KEY_ALIAS` | For automatic releases | Alias of the signing key inside the keystore |
| `ANDROID_KEY_PASSWORD` | For automatic releases | Password of the signing key (same as the keystore password for a PKCS12 keystore) |

All four must be set together. If any is missing, the release workflow publishes nothing and says so in the job
summary. Publishing uses the built-in `GITHUB_TOKEN` (the workflow requests `contents: write` itself), so **no
personal access token is needed**.

No other secrets exist:

- The **AI assistant** is optional and bring-your-own-key. The end user types their own provider key into the app,
  where it is stored with the Android Keystore on the device. There is no backend and no CI secret for it.
- **Documentation updates** are fetched from the public URL
  `https://raw.githubusercontent.com/riyadmondol2006/Smali-Helper-Plus/main/content/manifest.json`. No token needed.
- There is **no `.env.example`** because the project has no environment configuration to copy. Environment files
  (`.env`, `.env.*`) are ignored by git and flagged by `scripts/check-secrets.sh`.

Repository **variables** (Settings > Secrets and variables > Actions > Variables) are not used. There is no
non-sensitive setting that needs to be configured. The version number is not a variable either: it is derived from
the git commit count automatically (see [CI_SETUP.md](CI_SETUP.md#automatic-versioning)).

One repository setting must allow the workflow to publish: **Settings > Actions > General > Workflow permissions**.
Either choice works because `release.yml` requests `contents: write` explicitly, but if your organization enforces
read-only tokens, allow "Read and write permissions" for this repository.

## How the build uses them

`app/build.gradle.kts` reads these environment variables:

| Variable | Set by the release workflow from |
|---|---|
| `ANDROID_KEYSTORE_PATH` | the decoded keystore file at `$RUNNER_TEMP/release.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | secret `ANDROID_KEYSTORE_PASSWORD` |
| `ANDROID_KEY_ALIAS` | secret `ANDROID_KEY_ALIAS` |
| `ANDROID_KEY_PASSWORD` | secret `ANDROID_KEY_PASSWORD` |
| `SMALI_HELPER_REQUIRE_SIGNING=true` | the workflow: Gradle then fails if signing is incomplete instead of producing an unsigned APK |
| `SMALI_HELPER_GIT_SHA` | the released commit, shown on the app's About screen |

The workflow decodes the keystore with `umask 077`, passes the passwords only through the environment of the Gradle
step, never prints them, verifies the signature with `apksigner`, and deletes the keystore in an `if: always()` step.
The keystore is never inside an uploaded artifact path.

## Create a release keystore

Run this on your own machine, not in CI:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias <alias>
```

Choose a strong password when asked. Press Enter at "Enter key password" to reuse the keystore password. Then check it:

```bash
keytool -list -v -keystore release.jks
```

Back it up before you do anything else (see "Backup and rotation").

## Add the secrets to GitHub

1. Encode the keystore on a single line:

   ```bash
   base64 -w0 release.jks > release.jks.b64     # Linux
   base64 -i release.jks | tr -d '\n' > release.jks.b64   # macOS
   ```

2. In the repository open **Settings > Secrets and variables > Actions > New repository secret** and create the four
   secrets listed above. For `ANDROID_KEYSTORE_BASE64` paste the content of `release.jks.b64`.

   With the GitHub CLI you can do the same from a terminal, the values never appear in your shell history when you let
   `gh` prompt for them:

   ```bash
   gh secret set ANDROID_KEYSTORE_BASE64 < release.jks.b64
   gh secret set ANDROID_KEYSTORE_PASSWORD
   gh secret set ANDROID_KEY_ALIAS
   gh secret set ANDROID_KEY_PASSWORD
   ```

3. Delete the temporary encoded file: `rm release.jks.b64`.
4. Push to `main` (or run **Actions > Release > Run workflow** on `main`). After Android CI passes, the Release
   workflow publishes `v1.0.N` under **Releases** with the signed APK attached.

## Rules

- **Never commit** keystores, passwords, `keystore.properties`, `.env` files, service-account JSON or API keys.
  `.gitignore` blocks the common names, and `scripts/check-secrets.sh` scans the tree (run it before pushing, you can
  also run it from a pre-push hook or CI step).
- GitHub does not pass secrets to workflows triggered from forks, so pull request builds are always unsigned debug builds.
- Keep `android-ci.yml` at `permissions: contents: read`. Only `release.yml` has `contents: write`, and it only runs
  for successful pushes to `main` (never for pull requests or forks).
- Always sign with the same key: Android only installs an update over an existing install when the signatures match.

## Backup and rotation

- Store `release.jks` and its passwords in at least two separate places you control (for example a password manager
  and an encrypted offline drive). If you lose the key you can no longer ship updates under the same signature.
- Never rely on the GitHub secret as your only copy: secrets cannot be read back.
- To rotate a password or the whole key, create a new keystore, update all four secrets, and note that Android only
  accepts updates signed by the same key (or a key rotated with APK Signature Scheme v3 lineage). For a leaked
  keystore or password: remove the secrets, create a new key, and treat old builds as untrusted.
- If a secret was ever committed or pushed, rotate it. Removing it from git history is not enough.
