#!/usr/bin/env bash
# Does Workbench still return only the transformed text?
#
#   tools/workbench_check.sh <label>
#
# Workbench is the one mode no battery here can reach. It is not a chat mode: it
# has its own screen, with the text in one box and the instruction chosen from
# buttons, so nothing that types into a composer touches it. That is why it gets
# missed, and it is a reason to check it separately rather than to skip it.
#
# The specific worry, from the standing check on shared prompt changes: the hard
# rules now say a bare statement gets one line that adds to it, and a Workbench
# request is a statement plus an instruction. If the shared rule wins over the
# mode rule, the reply becomes a remark about the text instead of the text. Its
# contract is to start with the first word of the result, with no preamble and no
# commentary.
#
# Coordinates are read off the Workbench screen at 1080x2404 in portrait: the
# text box, then the first preset button. Both are checked by capture rather than
# assumed, because a tap that lands somewhere else produces a screenshot that
# looks like a result.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

label="${1:?usage: workbench_check.sh <label>}"
out="/tmp/bench-$label"
mkdir -p "$out"

# Deliberately a plain declarative paragraph, which is the shape the shared rule
# is about, and one that obviously wants tightening so a correct answer is
# recognisable at a glance.
TEXT="We are writing to inform you that at this point in time we have made the decision to go ahead with the proposal that was discussed last week."

prior_rotation="$("$adb" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
restore() {
  [ "${prior_rotation:-1}" = "1" ] && \
    "$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
}
trap restore EXIT
"$adb" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$adb" shell settings put system user_rotation 0 >/dev/null 2>&1 || true

"$adb" logcat -c >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8
"$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 2
"$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
"$adb" shell input tap 143 2270          # Chats
sleep 2
"$adb" shell input tap 925 2122          # Workbench
sleep 7
./tools/shot.sh "$out/01-opened.png" >/dev/null 2>&1 || true

# The text box, then clear whatever a previous session left in it.
"$adb" shell input tap 540 700
sleep 1
"$adb" shell input keycombination 113 29 >/dev/null 2>&1 || true
sleep 1
"$adb" shell input keyevent 67 >/dev/null 2>&1 || true
sleep 1
escaped="$(printf '%s' "$TEXT" | sed 's/ /%s/g')"
"$adb" shell input text "$escaped"
sleep 2
if "$adb" shell dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then
  "$adb" shell input keyevent KEYCODE_BACK
  sleep 1
fi
./tools/shot.sh "$out/02-typed.png" >/dev/null 2>&1 || true

# Tighten, the first preset.
"$adb" shell input tap 115 1053
echo "  sent, waiting for the result"

# Same completion wait as everywhere else: the engine says when it has finished.
before=""
deadline=$((SECONDS + 200))
while [ "$SECONDS" -lt "$deadline" ]; do
  sleep 3
  now="$("$adb" logcat -d -s KamPerf 2>/dev/null | grep 'decode=' | tail -1 || true)"
  if [ -n "$now" ] && [ "$now" != "$before" ]; then
    before="$now"
    sleep 6
    break
  fi
done
sleep 2
./tools/shot.sh "$out/03-result.png" >/dev/null 2>&1 || true

echo
echo "== what the engine did =="
"$adb" logcat -d -s KamPerf 2>/dev/null | grep 'decode=' | tail -2
echo
echo "read $out/03-result.png: the result should be the tightened text and"
echo "nothing else, no preamble and no remark about the text."
