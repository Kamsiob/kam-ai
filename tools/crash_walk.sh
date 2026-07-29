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
check() {  # name
  step=$((step+1))
  sleep 3
  local crashes
  crashes="$(adb_count "crash log lines" "com.kamsiob.kamai" "$adb" logcat -d -b crash)"
  local anrs
  anrs="$(adb_count "ANR lines" "ANR in com.kamsiob.kamai" "$adb" logcat -d)"
  if [ "$crashes" -gt 0 ] || [ "$anrs" -gt 0 ]; then
    printf '  %02d  CRASH after: %s  (crash lines %s, anr %s)\n' "$step" "$1" "$crashes" "$anrs"
    fails=$((fails+1))
  else
    printf '  %02d  ok: %s\n' "$step" "$1"
  fi
  capture_or_note tools "$out/$(printf '%02d' $step).png"
}

tap() { "$adb" shell input tap "$1" "$2"; }

"$adb" logcat -c -b crash >/dev/null 2>&1 || true
"$adb" logcat -c >/dev/null 2>&1 || true
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
  tap 526 "$y"; check "settings row y=$y"
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
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
