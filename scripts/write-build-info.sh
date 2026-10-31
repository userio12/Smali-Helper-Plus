#!/usr/bin/env bash
# Writes build metadata as JSON.
#
# Usage: scripts/write-build-info.sh <output-file>
# Environment (all optional; GitHub Actions provides the GITHUB_* values):
#   VERSION_NAME / VERSION_CODE  (default: read from ./gradlew printVersion)
#   BUILD_COMMIT_SHA             (default: GITHUB_SHA, then git HEAD)
#   BUILD_VARIANT  debug | release (default: debug)
#   BUILD_SIGNED   true | false    (default: false)
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <output-file>" >&2
  exit 64
fi
out="$1"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The version comes from Gradle (git commit count + version.properties). Callers that already know it can pass
# VERSION_NAME / VERSION_CODE to skip the Gradle invocation.
read_gradle_version() {
  "$root/gradlew" -p "$root" -q printVersion --console=plain 2>/dev/null | sed -n -E "s/.*$1=([^ ]+).*/\1/p" | tail -n 1
}

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr -d '\n\r\t'
}

version_name="${VERSION_NAME:-$(read_gradle_version versionName)}"
version_code="${VERSION_CODE:-$(read_gradle_version versionCode)}"
if [ -z "$version_name" ] || [ -z "$version_code" ]; then
  echo "could not determine versionName/versionCode (./gradlew printVersion)" >&2
  exit 1
fi
case "$version_code" in *[!0-9]*) echo "versionCode must be numeric" >&2; exit 1 ;; esac

sha="${BUILD_COMMIT_SHA:-${GITHUB_SHA:-}}"
if [ -z "$sha" ]; then
  sha="$(git -C "$root" rev-parse --verify -q HEAD 2>/dev/null || echo local)"
fi
ref="${GITHUB_REF:-$(git -C "$root" symbolic-ref -q HEAD 2>/dev/null || echo local)}"
variant="${BUILD_VARIANT:-debug}"
signed="${BUILD_SIGNED:-false}"
case "$signed" in true | false) ;; *) signed=false ;; esac
run_number="${GITHUB_RUN_NUMBER:-0}"
run_id="${GITHUB_RUN_ID:-0}"
case "$run_number$run_id" in *[!0-9]*) echo "run number/id must be numeric" >&2; exit 1 ;; esac

mkdir -p "$(dirname "$out")"
cat > "$out" <<JSON
{
  "app": "Smali Helper Plus",
  "applicationId": "com.riyadm.smalihelperplus",
  "variant": "$(json_escape "$variant")",
  "signed": $signed,
  "versionName": "$(json_escape "$version_name")",
  "versionCode": $(json_escape "$version_code"),
  "commitSha": "$(json_escape "$sha")",
  "ref": "$(json_escape "$ref")",
  "repository": "$(json_escape "${GITHUB_REPOSITORY:-local}")",
  "event": "$(json_escape "${GITHUB_EVENT_NAME:-local}")",
  "workflow": "$(json_escape "${GITHUB_WORKFLOW:-local}")",
  "runNumber": $run_number,
  "runId": $run_id,
  "builtAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
JSON
echo "wrote $out"
