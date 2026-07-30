#!/usr/bin/env bash
# The honest range for how long an answer takes, so the copy can reflect the worst
# realistic case rather than the best measured one.
#
#   tools/first_answer_range.sh [label]
#
# ## Why this exists
#
# Someone downloads several gigabytes, sends their first message, and waits. If they do
# not know why, they conclude the application is broken and uninstall it, and that
# judgement is made once and never revisited. **This is the most likely reason a new user
# leaves**, and copy written from the best measured number would make it worse rather than
# better: a user told "about three seconds" who waits twelve has been misled, which is
# worse than not being told.
#
# So this measures the cases a real person actually meets, in the order they meet them.
#
# ## What each case is, and why it is not the same as the one before it
#
#   cold      The model is not loaded. Force-stopped first, so the engine must read the
#             weights before it can begin. This is the proxy for the first message of a
#             later session. **The true first message after installing is worse than this**,
#             because the file has never been read and nothing is in the page cache, and
#             that cannot be reproduced here without an uninstall.
#   warm      A fresh conversation with the model already loaded. The ordinary case, and
#             the one every previous measurement here has reported.
#   memory    A turn whose words overlap a stored topical fact, in a held conversation.
#             #143 measured this path at 444 tokens of prefill and 12.5 seconds, against
#             35 to 45 tokens and about 1.5 seconds for its neighbours, on 17% of turns.
#
# Thermal state is recorded with every reading, because a warm phone was measured at
# roughly threefold degradation and a number taken on a cool phone is not the number a
# user gets on a hot one.
set -euo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/lib/loud.sh"
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
label="${1:-range}"
out="/tmp/first-answer-$label"
mkdir -p "$out"

prior_rotation="$("$adb" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
restore_device() {
  [ "${prior_rotation:-1}" = "1" ] && \
    "$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
}
trap restore_device EXIT

thermal() {
  "$adb" shell dumpsys thermalservice 2>/dev/null |
    grep -m1 -oE 'Thermal Status: [0-9]+' | grep -oE '[0-9]+' || echo "?"
}

# The last completed generation's perf line, which carries TTFT and prefill.
last_perf() {
  "$adb" logcat -d -s KamPerf 2>/dev/null |
    grep -oE 'mode=[A-Z]+ TTFT=[0-9]+ms prefill=[0-9]+tok' | tail -1 || true
}

fresh_chat() {
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 6
  fi
  "$adb" shell input tap 143 2270
  sleep 2
  "$adb" shell input tap 160 2122
  sleep 6
}

record() {  # case-name
  local line temp
  temp="$(thermal)"
  line="$(last_perf)"
  if [ -z "$line" ]; then
    echo "  $1: NO PERF LINE, not measured" >&2
    EVIDENCE_MISSES=$((EVIDENCE_MISSES + 1))
    printf '%s\tMISSING\tthermal=%s\n' "$1" "$temp" >> "$out/range.tsv"
  else
    printf '  %-10s %s  thermal=%s\n' "$1" "$line" "$temp"
    printf '%s\t%s\tthermal=%s\n' "$1" "$line" "$temp" >> "$out/range.tsv"
  fi
  "$adb" logcat -c >/dev/null 2>&1 || true
}

echo "== the honest range, $(date '+%H:%M') =="

# 1. Cold. Force-stop so the weights must be read again.
"$adb" shell am force-stop com.kamsiob.kamai >/dev/null 2>&1 || true
sleep 3
"$adb" logcat -c >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8
"$adb" shell input tap 143 2270; sleep 2
"$adb" shell input tap 160 2122; sleep 6
./tools/say.sh "What is a good way to keep track of small repairs around the house?" 300 >/dev/null
record cold

# 2. Warm, a fresh conversation with the model already loaded.
fresh_chat
./tools/say.sh "What is the difference between baking soda and baking powder?" 180 >/dev/null
record warm

# 3. Warm again, so the ordinary case has two readings rather than one.
fresh_chat
./tools/say.sh "How do I get a coffee stain out of a rug?" 180 >/dev/null
record warm2

# 4. The memory path. Held conversation, second turn overlapping a topical memory.
fresh_chat
./tools/say.sh "What should I ask when viewing a flat?" 180 >/dev/null
"$adb" logcat -c >/dev/null 2>&1 || true
./tools/say.sh "Is the rowing club open on Sunday?" 300 >/dev/null
record memory

cat <<EOF

Readings in $out/range.tsv

Copy must reflect the worst realistic case, not the best. The true first message after
installing is worse than the cold reading here, because that file has never been read.
EOF
evidence_exit
