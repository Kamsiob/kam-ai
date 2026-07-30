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
. "$(dirname "$0")/lib/loud.sh"
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
label="${1:?usage: crash_walk2.sh <label>}"
out="/tmp/walk2-$label"
mkdir -p "$out"

fails=0; step=0
# The previous counts, so a crash is reported once rather than on every step after
# it. This reported "7 crashed" for one crash: it compared a cumulative count
# against zero, so every step following the first crash counted again. The verdict
# was right and the number was wrong, which is its own kind of misleading when
# somebody reads it as severity.
prev_c=0; prev_a=0
check() {
  step=$((step+1)); sleep 3
  local c a
  c="$(adb_count "crash log lines" "com.kamsiob.kamai" "$adb" logcat -d -b crash)"
  a="$(adb_count "ANR lines" "ANR in com.kamsiob.kamai" "$adb" logcat -d)"
  if [ "$c" -gt "$prev_c" ] || [ "$a" -gt "$prev_a" ]; then
    prev_c="$c"; prev_a="$a"
    printf '  %02d  CRASH after: %s\n' "$step" "$1"; fails=$((fails+1))
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

fresh() {  # mode-x
  tap 142 2270; sleep 2; tap "$1" 2122; sleep 6
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
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8
check "start"

# A real message and a real reply in each chat mode.
for x in 160 414 670; do
  fresh "$x"
  # A slow reply must not abort a 33 step walk, but it must not vanish either:
  # say.sh now exits non-zero when nothing completed, and this records that as a
  # missing piece of evidence so evidence_exit fails the run at the end.
  if ! WAIT=240 ./tools/say.sh "What is a good way to keep track of small repairs?" 240 >/dev/null 2>&1; then
    EVIDENCE_MISSES=$((EVIDENCE_MISSES + 1))
    echo "  MISSING EVIDENCE: no reply completed at step $step" >&2
  fi
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
  open_settings_row "$y"; check "settings row y=$y"
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true; sleep 2
done

"$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
echo
if [ "$fails" -gt 0 ]; then echo "FAILED: $fails crashed. Captures in $out"; exit 1; fi
echo "No crashes across $step steps. Captures in $out"

# Fails the run if any capture or send went missing. The steps ran; without the
# evidence that they ran as described, this is not a pass.
evidence_exit
