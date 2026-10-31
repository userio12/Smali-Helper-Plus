#!/usr/bin/env bash
# Local smoke test of Smali Helper Plus on one connected Android device (primary target: Google Pixel 6).
#
# Steps: build debug APK -> install (keeps app data) -> cold-launch timing -> light/dark and font-scale
# screenshots -> landscape screenshot -> background/process-death/relaunch -> offline launch -> crash check ->
# ./gradlew connectedDebugAndroidTest.
#
# Safety:
#   * Changes to system settings (night mode, font scale, rotation, display density, airplane mode) are
#     recorded first and restored on exit, also on Ctrl+C or failure.
#   * The only app data touched is that of com.riyadm.smalihelperplus. Nothing else is cleared or uninstalled.
#     Note: connectedDebugAndroidTest (last step) uninstalls the app and its test APK when it finishes, which
#     removes the app's own data. Use --skip-instrumented to keep the app installed.
#
# Usage: scripts/pixel6-local-test.sh [options]
#   -s, --serial SERIAL   adb serial (required when more than one device is attached)
#   --skip-build          reuse app/build/outputs/apk/debug/app-debug.apk
#   --skip-instrumented   do not run connectedDebugAndroidTest
#   --no-offline          do not toggle airplane mode
#   --runs N              number of cold-launch timing runs (default 5)
#   --out DIR             output directory (default build/pixel6)
#   -h, --help
# Output: PNG screenshots, logcat crash dump and summary.txt in the output directory.
set -uo pipefail

PKG="com.riyadm.smalihelperplus"
ACTIVITY="$PKG/.MainActivity"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/build/pixel6"
serial="${ANDROID_SERIAL:-}"
skip_build=false
skip_instrumented=false
no_offline=false
runs=5

while [ "$#" -gt 0 ]; do
  case "$1" in
    -s | --serial) serial="${2:?missing serial}"; shift 2 ;;
    --skip-build) skip_build=true; shift ;;
    --skip-instrumented) skip_instrumented=true; shift ;;
    --no-offline) no_offline=true; shift ;;
    --runs) runs="${2:?missing count}"; shift 2 ;;
    --out) out="${2:?missing dir}"; shift 2 ;;
    -h | --help) sed -n '2,/^set -/{/^set -/!p}' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 64 ;;
  esac
done
case "$runs" in '' | *[!0-9]*) echo "--runs must be a number" >&2; exit 64 ;; esac

command -v adb > /dev/null || { echo "adb not found in PATH" >&2; exit 1; }
if [ -z "$serial" ]; then
  mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [ "${#devices[@]}" -ne 1 ]; then
    echo "expected exactly one attached device, found ${#devices[@]}; pass --serial" >&2
    exit 1
  fi
  serial="${devices[0]}"
fi
export ANDROID_SERIAL="$serial"

if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/java-21-openjdk ]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
fi

dsh() { adb shell "$@" | tr -d '\r'; }
mkdir -p "$out"
summary="$out/summary.txt"
: > "$summary"
log() { printf '%s\n' "$*" | tee -a "$summary"; }
failures=0
fail() { log "FAIL: $*"; failures=$((failures + 1)); }

model="$(dsh getprop ro.product.model)"
device="$(dsh getprop ro.product.device)"
release="$(dsh getprop ro.build.version.release)"
sdk="$(dsh getprop ro.build.version.sdk)"
log "Device: $model ($device), Android $release (API $sdk), serial $serial"
[ "$device" = "oriole" ] || log "NOTE: this is not a Pixel 6 (oriole); results are still valid but not Pixel 6 specific."
app_running() { [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; }
log "Screen: $(sh wm size | tr '\n' ' ') $(sh wm density | tr '\n' ' ')"

# --- Record settings to restore ---------------------------------------------------------------------------------
orig_night="$(dsh cmd uimode night | sed -n 's/.*: //p' | head -n 1)"
orig_font="$(dsh settings get system font_scale)"
orig_accel="$(dsh settings get system accelerometer_rotation)"
orig_rot="$(dsh settings get system user_rotation)"
orig_airplane="$(dsh settings get global airplane_mode_on)"
orig_density_override="$(dsh wm density | sed -n 's/^Override density: //p')"

restore() {
  trap - EXIT INT TERM
  echo "Restoring device settings..."
  case "$orig_night" in yes | no | auto) adb shell cmd uimode night "$orig_night" > /dev/null 2>&1 ;; *) adb shell cmd uimode night auto > /dev/null 2>&1 ;; esac
  if [ -z "$orig_font" ] || [ "$orig_font" = "null" ]; then adb shell settings delete system font_scale > /dev/null 2>&1; else adb shell settings put system font_scale "$orig_font" > /dev/null 2>&1; fi
  [ -n "$orig_rot" ] && [ "$orig_rot" != "null" ] && adb shell settings put system user_rotation "$orig_rot" > /dev/null 2>&1
  [ -n "$orig_accel" ] && [ "$orig_accel" != "null" ] && adb shell settings put system accelerometer_rotation "$orig_accel" > /dev/null 2>&1
  if [ -n "$orig_density_override" ]; then adb shell wm density "$orig_density_override" > /dev/null 2>&1; else adb shell wm density reset > /dev/null 2>&1; fi
  if [ "$airplane_toggled" = true ]; then
    if [ "$orig_airplane" = "1" ]; then adb shell cmd connectivity airplane-mode enable > /dev/null 2>&1; else adb shell cmd connectivity airplane-mode disable > /dev/null 2>&1; fi
  fi
}
airplane_toggled=false
trap restore EXIT INT TERM

# --- Build and install ------------------------------------------------------------------------------------------
apk="$root/app/build/outputs/apk/debug/app-debug.apk"
if [ "$skip_build" = false ]; then
  log "Building debug APK..."
  (cd "$root" && ./gradlew assembleDebug --console=plain) > "$out/build.log" 2>&1 || { tail -n 30 "$out/build.log"; echo "build failed; see $out/build.log" >&2; exit 1; }
fi
[ -f "$apk" ] || { echo "APK not found: $apk" >&2; exit 1; }
log "Installing $apk (existing app data is kept)..."
adb install -r "$apk" > "$out/install.log" 2>&1 || { cat "$out/install.log" >&2; exit 1; }

adb logcat -b crash -c > /dev/null 2>&1

shot() { # shot <name>
  sleep 2
  adb exec-out screencap -p > "$out/$1.png" && log "screenshot: $out/$1.png"
}
cold_launch() {
  adb shell am force-stop "$PKG"
  sleep 1
  adb shell am start -W -n "$ACTIVITY" | tr -d '\r'
}

# --- Cold launch timing -----------------------------------------------------------------------------------------
log "Cold launch timing ($runs runs, adb shell am start -W; TotalTime in ms):"
times=()
for i in $(seq 1 "$runs"); do
  t="$(cold_launch | sed -n 's/^TotalTime: //p')"
  if [ -z "$t" ]; then fail "run $i: no TotalTime reported (launch failed?)"; continue; fi
  log "  run $i: ${t} ms"
  times+=("$t")
done
if [ "${#times[@]}" -gt 0 ]; then
  sorted="$(printf '%s\n' "${times[@]}" | sort -n)"
  median="$(echo "$sorted" | sed -n "$(((${#times[@]} + 1) / 2))p")"
  log "  min $(echo "$sorted" | head -n 1) ms, median $median ms, max $(echo "$sorted" | tail -n 1) ms"
fi

# --- Theme and font scale matrix --------------------------------------------------------------------------------
adb shell am force-stop "$PKG"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
for mode in no yes; do
  for scale in 1.0 1.3; do
    adb shell cmd uimode night "$mode" > /dev/null
    adb shell settings put system font_scale "$scale"
    cold_launch > /dev/null
    label="night-${mode}_font-${scale}"
    shot "$label"
  done
done
adb shell am force-stop "$PKG"
adb shell settings put system font_scale 2.0
cold_launch > /dev/null
shot "night-yes_font-2.0"
adb shell settings put system font_scale 1.0

# --- Landscape --------------------------------------------------------------------------------------------------
adb shell settings put system user_rotation 1
sleep 2
shot "landscape"
adb shell settings put system user_rotation 0

# --- Display size -----------------------------------------------------------------------------------------------
adb shell wm density 560
sleep 2
shot "density-560"
if [ -n "$orig_density_override" ]; then adb shell wm density "$orig_density_override"; else adb shell wm density reset; fi

# --- Background, process death, relaunch ------------------------------------------------------------------------
adb shell cmd uimode night "$orig_night" > /dev/null 2>&1
cold_launch > /dev/null
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am kill "$PKG"
sleep 1
if app_running; then log "note: process still alive after am kill (it was not cached/background)"; else log "process killed in background, as expected"; fi
adb shell am start -n "$ACTIVITY" > /dev/null
sleep 3
if app_running; then log "relaunch after process death: ok"; else fail "app not running after relaunch following process death"; fi
shot "after-process-death"

# --- Offline ----------------------------------------------------------------------------------------------------
if [ "$no_offline" = true ] || [[ "$serial" == *:* ]]; then
  log "Offline check skipped (disabled, or wireless adb would disconnect)."
else
  adb shell cmd connectivity airplane-mode enable > /dev/null 2>&1 && airplane_toggled=true
  sleep 3
  cold_launch > /dev/null
  if app_running; then log "offline launch: ok"; else fail "app not running when launched offline"; fi
  shot "offline"
  if [ "$orig_airplane" = "1" ]; then adb shell cmd connectivity airplane-mode enable > /dev/null 2>&1; else adb shell cmd connectivity airplane-mode disable > /dev/null 2>&1; fi
  airplane_toggled=false
fi

# --- Frame stats and crash check --------------------------------------------------------------------------------
adb shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile' | tee -a "$summary" > /dev/null
adb logcat -b crash -d > "$out/crash.log" 2>&1
if grep -q "$PKG" "$out/crash.log"; then
  fail "crash entries for $PKG in the crash buffer, see $out/crash.log"
else
  log "crash buffer: no entries for $PKG"
fi

# --- Instrumented tests -----------------------------------------------------------------------------------------
if [ "$skip_instrumented" = false ]; then
  log "Running ./gradlew connectedDebugAndroidTest ..."
  if (cd "$root" && ./gradlew connectedDebugAndroidTest --console=plain) > "$out/connected.log" 2>&1; then
    log "connectedDebugAndroidTest: PASSED (reports in app/build/reports/androidTests/connected/)"
  else
    tail -n 40 "$out/connected.log"
    fail "connectedDebugAndroidTest failed, see $out/connected.log"
  fi
fi

log "Done with $failures failure(s). Summary: $summary"
[ "$failures" -eq 0 ]
