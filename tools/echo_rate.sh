#!/usr/bin/env bash
# Sends one message N times, each in a fresh chat, and counts how often the reply
# guard rejected a draft.
#
#   tools/echo_rate.sh <runs> <mode-x> "<message>"
#
# Exists because "about one in three" came from four runs and cannot tell one in
# three from one in five. A rate needs counting, and a rate is what #122 is: the
# app answers this input correctly most of the time and not always.
#
# The count comes from the guard's own log rather than from reading replies,
# because the guard now names the check and the matched text on every rejection.
# That measures how often the model *produced* an echo, which is the thing a
# sampler change would move, rather than how often one survived to the screen.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

runs="${1:?usage: echo_rate.sh <runs> <mode-x> <message>}"
mode_x="${2:?}"
message="${3:?}"
log="/tmp/echo-rate-$$.txt"

"$adb" logcat -c
("$adb" logcat -v time > "$log" 2>&1 &)
sleep 2

for i in $(seq 1 "$runs"); do
  WAIT="${WAIT:-55}" ./tools/session.sh "$mode_x" "rate$i" <<< "$message" >/dev/null 2>&1 || true
  printf '.'
done
printf '\n'

sleep 2
pkill -f 'adb logcat' 2>/dev/null || true

rejected=$(grep -c 'KamEcho.*rejected' "$log" || true)
replies=$(grep -c 'KamPerf.*TTFT=' "$log" || true)
echo "runs=$runs drafts=$replies rejections=$rejected"
echo "--- by check:"
grep -oE 'check=[a-z-]+' "$log" | sort | uniq -c || true
echo "--- log kept at $log"
