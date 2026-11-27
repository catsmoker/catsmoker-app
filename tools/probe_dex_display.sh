#!/bin/sh
# Read-only Samsung DeX display probe for CatSmoker.
#
# Answers, on the device that is actually plugged in, the four questions the
# research could not answer without Samsung hardware:
#
#   1. Does this firmware define Samsung's forcePacesetter / resetForcedPacesetter,
#      and which transaction codes does it use?
#   2. Can this build reach the composer's display-mode policy at all (i.e. are the
#      display-identity accessors present)?
#   3. Does it expose enableHighResolutionsForExternalDesktop, and does
#      SurfaceFlinger publish a forced-pacesetter field we could verify against?
#   4. What modes does the attached monitor actually enumerate, and what is it
#      running at right now?
#
# Nothing is written. No mode is switched, no setting is touched. Applying a mode
# is deliberately NOT in this script: that is the change that can blank a monitor,
# and CatSmoker only performs it behind its confirmation countdown. Use the app for
# that (Gaming Tools > Graphics & Display > Refresh rate) with the monitor in front
# of you.
#
# Usage:
#   sh tools/probe_dex_display.sh <serial>          # e.g. R5CT30XXXXX
#   sh tools/probe_dex_display.sh                   # single attached device
#
# Requires: adb, and the Full (GitHub) build installed -- the Play build has no
# display helper to probe, by design.

set -eu

SERIAL="${1:-}"
ADB_BIN="${ADB:-adb}"

if [ -n "$SERIAL" ]; then
    adb() { "$ADB_BIN" -s "$SERIAL" "$@"; }
else
    adb() { "$ADB_BIN" "$@"; }
fi

say() { printf '%s\n' "$*"; }
rule() { say "------------------------------------------------------------"; }

rule
say "Device"
rule
adb shell 'printf "  manufacturer : %s\n  model        : %s\n  android      : %s\n  oneui        : %s\n  build        : %s\n" \
    "$(getprop ro.product.manufacturer)" "$(getprop ro.product.model)" \
    "$(getprop ro.build.version.release)" \
    "$(getprop ro.build.version.oneui)" "$(getprop ro.build.display.id)"'

MANUFACTURER=$(adb shell getprop ro.product.manufacturer | tr -d '\r' | tr '[:upper:]' '[:lower:]')
if [ "$MANUFACTURER" != "samsung" ]; then
    say ""
    say "Not a Samsung device. The DeX and pacesetter answers only exist there;"
    say "on any other vendor this probe would report 'absent' for all of them, which"
    say "the app already handles by falling back to the settings rows. Continuing"
    say "anyway so the composer/pacesetter report is still visible."
fi

rule
say "Full build installed?"
rule
APK_PATH=$(adb shell pm path com.catsmoker.app 2>/dev/null | head -n 1 | tr -d '\r' | sed 's/^package://')
if [ -z "$APK_PATH" ]; then
    say "  com.catsmoker.app is not installed. Install the Full build first:"
    say "    ./gradlew :app:assembleFullDebug"
    say "    adb install -r app/build/outputs/apk/full/debug/app-full-universal-debug.apk"
    exit 1
fi
say "  $APK_PATH"

rule
say "Privileged display probe (runs as the identity adb gives us)"
rule
adb shell "CLASSPATH=$APK_PATH app_process /system/bin com.catsmoker.app.screens.gaming.display.DisplayShellMain probe" \
    || say "  the helper produced no report (R8 may have renamed the entry point)"

say ""
say "  Reading the four flags above:"
say "    uid             0 = root adbd, 2000 = shell -- both hold ACCESS_SURFACE_FLINGER"
say "    modeSpecs       the composer's display-mode policy is usable (needs all four)"
say "    displayIds /"
say "    displayToken    the identity accessors; removed by AOSP in Android 14/15,"
say "                    kept by Samsung. false here means the settings rows are used"
say "    pacesetter      Samsung's forcePacesetter exists and its codes were decoded"
say "    dexHighRes      enableHighResolutionsForExternalDesktop is present"
say "    schedulerDump   SurfaceFlinger publishes forcedPacesetterDisplayId, so a"
say "                    pacesetter change can be read back instead of assumed"

rule
say "SurfaceFlinger scheduler (pacesetter readback)"
rule
adb shell 'dumpsys SurfaceFlinger --scheduler 2>/dev/null | grep -i "forcedPacesetter" || echo "  no forcedPacesetter field in this build"' | tr -d '\r'

rule
say "Attached displays and the modes they enumerate"
rule
# One line per display plus one per enumerated mode. The raw dump is enormous and is not
# the point: what matters is which displays exist, what each is running, and every rate
# each one lists. Full output, if needed: adb shell dumpsys display
adb shell 'dumpsys display 2>/dev/null' \
    | grep -E 'DisplayDeviceInfo\{|DisplayModeRecord\{|mActiveModeId' \
    | tr -d '\r' | cut -c1-260 | sed 's/^ */  /'

say ""
say "  external displays attached: $(adb shell 'dumpsys display 2>/dev/null | grep -c "type EXTERNAL"' | tr -d '\r')"

rule
say "Thermal status (0 none .. 6 shutdown)"
rule
adb shell 'dumpsys thermalservice 2>/dev/null | grep -i "Thermal Status" || echo "  not reported"' | tr -d '\r' | sed 's/^/  /'

rule
say "DeX / desktop mode"
rule
adb shell 'dumpsys activity activities 2>/dev/null | grep -ciE "dex|desktopmode" | sed "s/^/  matching lines: /"' \
    | tr -d '\r' || say "  not reported"

rule
say "Done. Nothing was changed."
say "Attach a monitor, turn on DeX, then re-run to see the monitor's own mode list."
say "To change a mode, use the app: it snapshots first, counts down, and reverts itself."
rule
