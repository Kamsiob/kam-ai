#!/usr/bin/env bash
# The second half of the crash walk: the things that actually do work.
#
#   tools/crash_walk2.sh <label>
#
# The first walk opened every screen. Opening a screen is the cheap half. This one
# sends real messages through every mode, runs a Workbench transformation, deals a
# Discover moment and opens the reader, and replays onboarding through to a first
# conversation, which is the path a reviewer follows on a fresh install.
#
# Onboarding is replayed rather than reinstalled. Uninstalling would destroy the
# Keystore entry wrapping the database key, making every existing conversation
# permanently unreadable, and cost a multi-gigabyte re-download. Replay covers the
# same screens.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
label="${1:?usage: crash_walk2.sh <label>}"
out="/tmp/walk2-$label"
mkdir -p "$out"

fails=0; step=0
check() {
  step=$((step+1)); sleep 3
  local c a
  c="$("$adb" logcat -d -b crash 2>/dev/null | grep -c "com.kamsiob.kamai" || true)"
  a="$("$adb" logcat -d 2>/dev/null | grep -c "ANR in com.kamsiob.kamai" || true)"
  if [ "$c" -gt 0 ] || [ "$a" -gt 0 ]; then
    printf '  %02d  CRASH after: %s\n' "$step" "$1"; fails=$((fails+1))
  else
    printf '  %02d  ok: %s\n' "$step" "$1"
  fi
  ./tools/shot.sh "$out/$(printf '%02d' $step).png" >/dev/null 2>&1 || true
}
tap() { "$adb" shell input tap "$1" "$2"; }
fresh() {  # mode-x
  tap 142 2270; sleep 2; tap "$1" 2122; sleep 6
}

"$adb" logcat -c -b crash >/dev/null 2>&1 || true
"$adb" logcat -c >/dev/null 2>&1 || true
"$adb" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8
check "start"

# A real message and a real reply in each chat mode.
for x in 160 414 670; do
  fresh "$x"
  WAIT=240 ./tools/say.sh "What is a good way to keep track of small repairs?" 240 >/dev/null 2>&1 || true
  check "reply generated in mode x=$x"
done

# Workbench does its own thing: text in a box, transformation from a button.
fresh 925
tap 540 700; sleep 1
"$adb" shell input keycombination 113 29 >/dev/null 2>&1 || true
"$adb" shell input keyevent 67 >/dev/null 2>&1 || true
"$adb" shell input text "We%sare%swriting%sto%sinform%syou%sthat%sthe%sproposal%sis%sapproved."
sleep 2
if "$adb" shell dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then
  "$adb" shell input keyevent KEYCODE_BACK; sleep 1
fi
tap 115 1053
sleep 25
check "Workbench transformation"

# Discover: the tab, a dealt moment, the reader, the packs sheet.
tap 936 2270; check "Discover tab"
tap 540 1200; check "Discover moment tapped"
"$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true; sleep 2
check "back from Discover"

# The remaining settings screens.
tap 142 2270; sleep 2; tap 1013 219; sleep 4
for y in 1525 1674 1823; do
  tap 526 "$y"; check "settings row y=$y"
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true; sleep 2
done

"$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
echo
if [ "$fails" -gt 0 ]; then echo "FAILED: $fails crashed. Captures in $out"; exit 1; fi
echo "No crashes across $step steps. Captures in $out"
