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

# Where each run's measurement is kept as it happens.
#
# logcat is circular, and reading it once at the end loses the early runs: the #134
# thermal run sent 14 generations and only 2 lines survived to be read. That is the
# same defect fixed in prefix_probe.sh and not carried across to here, which is the
# third time this week a fix has been applied to one script and not its siblings.
lines_out="/tmp/perf-probe-lines.tsv"
: > "$lines_out"

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
  # Read this run's line now, then clear, so run 1 is still readable at run 14.
  printf '%s\t%s\n' "$i" \
    "$("$adb" logcat -d -s KamPerf 2>/dev/null | grep 'decode=' | tail -1 | tr -d '\r')" \
    >> "$lines_out"
  "$adb" logcat -c >/dev/null 2>&1 || true
  echo "run $i done"
done

echo
echo "=== what the engine measured, one line per run, none aged out"
cat "$lines_out"
