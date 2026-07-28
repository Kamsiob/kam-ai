#!/usr/bin/env bash
# Sends the same message N times, each in a fresh chat, and prints what the
# engine measured for each one.
#
#   tools/perf_probe.sh [runs] [wait-seconds] [mode-x]
#
# Exists because "is it faster" needs the same prompt, the same mode and the same
# starting state every time, and doing that by hand produces numbers that cannot
# be compared with each other. The prompt below asks for a long answer on
# purpose: a short generation measures load and warm-up rather than speed, and
# the G5 throttles, so a burst flatters itself.
#
# Navigation matters as much as the message. This taps the mode chip on the chat
# list to open a new conversation rather than assuming one is already open, after
# a probe that typed into the chat list, missed the composer entirely, and opened
# a browser over the app.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

runs="${1:-3}"
wait_s="${2:-55}"
mode_x="${3:-160}"   # General

"$adb" shell am force-stop com.brave.browser >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 6
"$adb" logcat -c

prompt="Explain in about eight sentences why bread needs a hot oven."

for i in $(seq 1 "$runs"); do
  "$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
  # Back out to the list, and relaunch if that left the app, since Back from the
  # list exits rather than going up.
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 5
  fi
  "$adb" shell input tap "$mode_x" 2122   # a fresh conversation in this mode
  sleep 6
  ./tools/say.sh "$prompt" "$wait_s" >/dev/null
  echo "run $i done"
done

echo
echo "=== what the engine measured"
"$adb" logcat -d -s KamPerf 2>/dev/null | grep -E 'TTFT=' || echo "no measurements captured"
