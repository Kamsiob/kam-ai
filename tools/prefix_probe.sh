#!/usr/bin/env bash
# Is the instruction block being re-read from the cache between turns?
#
#   tools/prefix_probe.sh <label> [turns]
#
# Time to first token in this application rests on one thing: the system block is
# the KV cache prefix, and a turn that finds it unchanged prefills about 35 tokens
# instead of 863. Every battery here opens a fresh conversation per message, so
# none of them has ever observed a second turn, which is the only place prefix
# reuse exists.
#
# That blind spot hid a real defect. Memories are injected into the system block
# and were written in ranked order, ranking depends on overlap with the current
# message, so the block was different text on every turn and the prefix was missed
# every time. Fixed by writing them newest first, which is stable.
#
# This measures the thing directly: hold one conversation for several turns and
# read the prefill token count out of each KamPerf line. A reused prefix reports a
# handful of tokens. A missed one reports the whole block.
#
# Run it with memories stored, since a user with nothing stored has a stable block
# either way and would show a false pass.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

label="${1:?usage: prefix_probe.sh <label> [turns]}"
out="/tmp/prefix-$label"
mkdir -p "$out"

prior_rotation="$("$adb" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
restore() {
  [ "${prior_rotation:-1}" = "1" ] && \
    "$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
}
trap restore EXIT
"$adb" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$adb" shell settings put system user_rotation 0 >/dev/null 2>&1 || true

# Deliberately about different subjects turn to turn. If the block were still
# ordered by relevance, changing subject is exactly what would reorder it, so
# these are the turns most likely to miss the cache.
turns=(
  "What is a good way to keep track of small repairs around the house?"
  "The back gate sticks whenever the wood swells."
  "How long should tea steep?"
  "What time do most libraries close on a Sunday?"
  "Remind me what we were talking about."
)

"$adb" logcat -c >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8

# One conversation, held. Not session.sh, because this needs the log cleared and
# read at a specific point rather than screenshots.
"$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 2
"$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
"$adb" shell input tap 143 2270
sleep 2
"$adb" shell input tap 160 2122
sleep 7

i=0
for t in "${turns[@]}"; do
  i=$((i+1))
  WAIT=200 ./tools/say.sh "$t" 200 >/dev/null 2>&1 || true
  ./tools/shot.sh "$out/$(printf '%02d' $i).png" >/dev/null 2>&1 || true
  printf '  turn %s sent\n' "$i"
done

echo
echo "== prefill tokens per turn, in order =="
echo "   a reused prefix is tens of tokens; a missed one is the whole block"
"$adb" logcat -d -s KamPerf 2>/dev/null | grep -oE 'mode=[A-Z]+ TTFT=[0-9]+ms prefill=[0-9]+tok' |
  sed 's/^/   /'
echo
echo "captures in $out"
