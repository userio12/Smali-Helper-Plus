# Testing

Smali Helper Plus is tested at three levels:

| Level | Command | Where it runs |
|---|---|---|
| JVM unit tests (parsers, validators, use cases, ViewModels) | `./gradlew testDebugUnitTest` | any machine with JDK 21, also CI |
| Compose UI / Hilt instrumented tests | `./gradlew connectedDebugAndroidTest` | emulator or device, also CI (Pixel 6 emulator, API 36) |
| Manual / scripted device matrix | `scripts/pixel6-local-test.sh` and the checklist below | the physical **Pixel 6** (Android 17, API 37) |

Lint: `./gradlew lintDebug`. Reports:

- Lint: `app/build/reports/lint-results-debug.html` and `.xml`
- Unit tests: `app/build/reports/tests/testDebugUnitTest/`, raw results in `app/build/test-results/`
- Instrumented tests: `app/build/reports/androidTests/connected/`, raw results in `app/build/outputs/androidTest-results/`

The instrumentation runner is `com.riyadm.smalihelperplus.HiltTestRunner`. The debug build has no applicationId suffix,
so the package on the device is `com.riyadm.smalihelperplus`.

## Reference device

Google Pixel 6 (`oriole`), 1080x2400 at 420 dpi, which is about 411x914 dp. Also check a tablet-width window (for
example a resizable emulator or the Pixel Tablet profile) because the navigation switches between bar and rail.

## Scripted run

With the phone connected over USB, USB debugging enabled and authorized:

```bash
adb devices                          # exactly one device in the "device" state
scripts/pixel6-local-test.sh         # add --serial <id> with several devices
```

Useful options: `--skip-build`, `--skip-instrumented`, `--no-offline`, `--runs 10`, `--out <dir>`. Output goes to
`build/pixel6/` (screenshots, `crash.log`, `summary.txt`, Gradle logs). The script records the device settings it
changes (night mode, font scale, rotation, display density, airplane mode) and restores them when it ends, including
on Ctrl+C. It only touches the data of `com.riyadm.smalihelperplus`. Note that `connectedDebugAndroidTest` uninstalls
the app when it finishes (use `--skip-instrumented` to keep it installed and keep its data).

## Manual device matrix (Pixel 6)

Set `PKG=com.riyadm.smalihelperplus` and `ACT=$PKG/.MainActivity`. All commands below use only the `shell` user and
are reversible.

### Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk     # -r keeps data; add -d to allow a downgrade
```

Expected: `Success`, app opens from the launcher, icon and name correct.

### Cold launch time

```bash
adb shell am force-stop $PKG
adb shell am start -W -n $ACT          # read TotalTime (ms); repeat 5 times and use the median
```

`ThisTime`/`TotalTime`/`WaitTime` are in milliseconds. Record the median; first launch after install also seeds the
bundled content into Room, so compare "first launch" and "later launches" separately.

### Dynamic color

Settings > Wallpaper & style: change the wallpaper color / color palette. With **Dynamic color** enabled in the app's
Settings, the app's primary/secondary colors follow the wallpaper. Turn it off and confirm the static brand scheme
is used. Check both in light and dark.

### Light / dark

```bash
adb shell cmd uimode night yes       # dark
adb shell cmd uimode night no        # light
adb shell cmd uimode night auto      # follow the system schedule (restore this at the end)
adb shell cmd uimode night           # prints the current mode
```

Also try the app's own theme setting (system / light / dark). Check code blocks and the Playground editor in both.

### Gesture vs three-button navigation

```bash
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural     # the Pixel default
adb shell cmd overlay list | grep navbar                                       # [x] marks the active one
```

Check edge-to-edge insets, that the bottom bar is not covered, and predictive back (swipe back from the edge shows
the back preview and returns to the previous screen).

### Font scale

```bash
adb shell settings put system font_scale 1.3
adb shell settings put system font_scale 2.0      # largest
adb shell settings put system font_scale 1.0      # normal (or: settings delete system font_scale)
```

Check that nothing is clipped, buttons stay reachable and code blocks scroll horizontally instead of wrapping oddly.

### Display size

```bash
adb shell wm density                  # shows physical and override density (Pixel 6: 420)
adb shell wm density 560              # larger display size
adb shell wm density reset            # always reset when done
```

### Rotation

```bash
adb shell settings put system accelerometer_rotation 0     # turn off auto-rotate first
adb shell settings put system user_rotation 1              # landscape (3 = reverse landscape)
adb shell settings put system user_rotation 0              # portrait
adb shell settings put system accelerometer_rotation 1     # back to auto-rotate
```

State must survive rotation: scroll position, search text, Playground code and quiz progress.

### Offline

```bash
adb shell cmd connectivity airplane-mode enable     # Android 11+; "airplane-mode" without argument prints the state
adb shell cmd connectivity airplane-mode disable
# alternative that leaves airplane mode alone:
adb shell svc wifi disable && adb shell svc data disable
adb shell svc wifi enable  && adb shell svc data enable
```

Expected offline: every screen except update checks and the AI assistant works; those show a clear error with
retry, never a crash. Do not use wireless adb for this step, airplane mode disconnects it.

### Background, foreground and process death

```bash
adb shell input keyevent KEYCODE_HOME          # send to background
adb shell am kill $PKG                         # kills the cached/background process (no effect on a foreground app)
adb shell am start -n $ACT                     # relaunch; or reopen from Recents
```

Expected: the app returns to a sensible screen with saved state (route arguments, Playground code, preferences)
restored. Also try "Don't keep activities" in Developer options for a harsher test.

### Crash check

```bash
adb logcat -b crash -c                          # clear before the session
# ...exercise the app...
adb logcat -b crash -d | grep -A 20 $PKG        # should print nothing
adb logcat -d -s AndroidRuntime:E               # fatal exceptions, if any
```

Also watch for ANRs: `adb shell dumpsys activity processes | grep -i anr`, and check the ANR dialog never appears
during import/export and update checks.

### Frame stats (optional)

```bash
adb shell dumpsys gfxinfo $PKG reset
# scroll long lists (opcode list, lessons) for 20 seconds
adb shell dumpsys gfxinfo $PKG | grep -E 'Total frames|Janky frames|percentile'
```

### Screenshots

```bash
adb exec-out screencap -p > build/pixel6/home.png
```

## Test matrix summary

| Check | Pass criteria |
|---|---|
| Install / upgrade | `Success`, data kept with `install -r` |
| Cold launch | Median `TotalTime` recorded, no visible white flash, splash then Home |
| Light / dark / dynamic color | All screens readable, contrast adequate, code colors adapt |
| Gesture + 3-button navigation | No content under system bars, back works |
| Font scale 1.3 and 2.0 | No clipped or overlapping text |
| Display size 560 dpi | Layout still usable |
| Portrait / landscape | State preserved |
| Offline | Core content works; network features fail gracefully |
| Background + process death | State restored, no crash |
| Crash buffer | Empty for the app package |
| `connectedDebugAndroidTest` | All tests pass |

## Create a Pixel 6 emulator (AVD)

```bash
export ANDROID_HOME=$HOME/Android/Sdk
SDKM=$ANDROID_HOME/cmdline-tools/latest/bin
$SDKM/sdkmanager "platform-tools" "emulator" "system-images;android-36;google_apis;x86_64"
echo no | $SDKM/avdmanager create avd -n pixel6_api36 -k "system-images;android-36;google_apis;x86_64" -d pixel_6
$ANDROID_HOME/emulator/emulator -avd pixel6_api36 -gpu auto
```

Check available profiles with `$SDKM/avdmanager list device` and images with `$SDKM/sdkmanager --list`. An API 37
image is `system-images;android-37.0;google_apis;x86_64`. On Linux the emulator needs `/dev/kvm`
(`ls -l /dev/kvm`, and your user in the `kvm` group). Then run:

```bash
./gradlew connectedDebugAndroidTest
```

`connected` tasks run on every attached device; with several devices attached set `ANDROID_SERIAL=<serial>`.
