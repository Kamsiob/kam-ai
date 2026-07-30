#!/usr/bin/env bash
# Walk every screen and report any crash.
#
#   tools/crash_walk.sh <label>
#
# A reviewer runs the application through a script and a single crash is a
# rejection, so this is the check that matters most before a submission. Run it
# against the releaseCheck build, which is identical to release in every way that
# can break at runtime: R8 minified, resources shrunk, the same keep rules. The
# difference is the signature, so it installs over the existing app instead of
# demanding an uninstall that would destroy the database key and a multi-gigabyte
# model.
#
# After every step it reads the crash buffer. A step that crashes is named, rather
# than the run simply ending, because "it crashed somewhere" costs an hour to
# narrow down.
set -euo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/lib/loud.sh"
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
label="${1:?usage: crash_walk.sh <label>}"
out="/tmp/walk-$label"
mkdir -p "$out"

fails=0
step=0
# The previous counts, so a crash is reported once rather than on every step after
# it. This reported "7 crashed" for one crash: it compared a cumulative count
# against zero, so every step following the first crash counted again. The verdict
# was right and the number was wrong, which is its own kind of misleading when
# somebody reads it as a severity.
prev_crashes=0
prev_anrs=0
check() {  # name
  step=$((step+1))
  sleep 3
  local crashes
  crashes="$(adb_count "crash log lines" "com.kamsiob.kamai" "$adb" logcat -d -b crash)"
  local anrs
  anrs="$(adb_count "ANR lines" "ANR in com.kamsiob.kamai" "$adb" logcat -d)"
  if [ "$crashes" -gt "$prev_crashes" ] || [ "$anrs" -gt "$prev_anrs" ]; then
    printf '  %02d  CRASH after: %s  (crash lines %s, anr %s)\n' "$step" "$1" "$crashes" "$anrs"
    prev_crashes="$crashes"
    prev_anrs="$anrs"
    fails=$((fails+1))
  else
    printf '  %02d  ok: %s\n' "$step" "$1"
  fi
  capture_or_note tools "$out/$(printf '%02d' $step).png"
}

tap() { "$adb" shell input tap "$1" "$2"; }

# The Settings screen contains destructive rows, so no tap here is allowed to be blind.
#
# **Why this is a hard rule and not caution.** These walks changed the active model (#149)
# by tapping fixed coordinates down Settings: y=780 is the Model row, and a back gesture
# that gets eaten leaves the next tap landing inside whatever screen is still open. The
# arithmetic is worse than that sounds. crash_walk taps y=1078, and "Forget everything" on
# the Memory screen sits at y~1067. **Eleven pixels.** One eaten back and the release gate
# script deletes the owner's memory.
#
# Settings also holds "Delete everything" at y~2044 and the "Confirm before deleting a
# chat" toggle at y~1928, either of which a shifted layout or a larger font scale could
# put under a blind tap.
#
# Two protections, because one is not enough:
#
#   1. Settings is re-established from the Chats tab before every row tap, so a tap can
#      never land in a screen left open by a failed back. Slower by two taps per row and
#      worth every one of them.
#   2. A hard ceiling. Nothing below SETTINGS_SAFE_MAX_Y is ever tapped, which keeps the
#      destructive rows out of reach even if the layout moves.
SETTINGS_SAFE_MAX_Y=1850

open_settings_row() {  # y
  local y="$1"
  if [ "$y" -gt "$SETTINGS_SAFE_MAX_Y" ]; then
    echo "REFUSING to tap Settings y=$y: below the safe ceiling of $SETTINGS_SAFE_MAX_Y." >&2
    echo "  Destructive rows live down there. Add the row by name if it must be covered." >&2
    exit 1
  fi
  # Re-establish a known screen rather than trusting the last back gesture.
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 1
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 1
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 5
  fi
  "$adb" shell input tap 142 2270    # Chats, a known screen
  sleep 2
  "$adb" shell input tap 1013 219    # the gear, from a known screen
  sleep 2
  "$adb" shell input tap 526 "$y"
  sleep 2
}


"$adb" logcat -c -b crash >/dev/null 2>&1 || true
"$adb" logcat -c >/dev/null 2>&1 || true
# Record what this script changes, and put it back. #149's lesson in one block.
#
# These walks set rotation and never restored it, so every run left auto-rotate off on
# somebody's phone. prefix_probe.sh has had this since it was written; the walks never
# did, and nothing noticed because a changed setting is invisible in the output.
#
# The rule now: a script that changes device or application configuration records the
# prior value and restores it on exit, including on failure, which is what the trap buys
# over restoring at the end.
prior_rotation="$("$adb" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
restore_device() {
  [ "${prior_rotation:-1}" = "1" ] && \
    "$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
}
trap restore_device EXIT
"$adb" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$adb" shell am force-stop com.kamsiob.kamai >/dev/null 2>&1 || true
sleep 2
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
check "cold start"

# The four tabs.
tap 142 2270; check "Chats tab"
tap 400 2270; check "Projects tab"
tap 671 2270; check "Follow-ups tab"
tap 936 2270; check "Discover tab"
tap 142 2270; check "back to Chats"

# The three list densities on Chats.
tap 878 393;  check "Chats view mode 1"
tap 962 393;  check "Chats view mode 2"
tap 1040 393; check "Chats view mode 3"
tap 878 393;  check "Chats view mode back"

# Search, typed and cleared.
tap 533 428; sleep 1; "$adb" shell input text "deadline"; check "search typed"
"$adb" shell input keycombination 113 29 >/dev/null 2>&1 || true
"$adb" shell input keyevent 67 >/dev/null 2>&1 || true
check "search cleared"
"$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true

# Every mode chip opens a conversation.
for x in 160 414 670 925; do
  tap 142 2270; sleep 2
  tap "$x" 2122; check "mode chip at x=$x"
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
done

# Settings, and every row on it.
tap 142 2270; sleep 2
tap 1013 219; check "Settings"
for y in 780 929 1078 1227 1376; do
  open_settings_row "$y"
  check "settings row y=$y"
done

"$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
echo
if [ "$fails" -gt 0 ]; then
  echo "FAILED: $fails step(s) crashed. Captures in $out"
  exit 1
fi
echo "No crashes across $step steps. Captures in $out"

# Fails the run if any capture or send went missing. The steps ran; without the
# evidence that they ran as described, this is not a pass.
evidence_exit
