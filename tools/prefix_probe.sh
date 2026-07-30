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
. "$(dirname "$0")/lib/loud.sh"
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
# One of these deliberately overlaps the *oldest* stored memory, and the rest do
# not.
#
# That is the whole design, and the first version got it wrong. With turns that
# share no word with anything stored, ranking by overlap and ranking by recency
# give the same order, so a run cannot tell a stable block from an unstable one
# and reports success either way. The overlapping turn is what pulls one memory to
# the front of the ranking, which is the moment the block changes.
#
# It has to be the oldest memory, which took two failed attempts to work out. If
# the overlapping turn matches the newest one, that memory was already first by
# recency, so the ranking puts it exactly where recency already had it and the
# block does not change. Only pulling an *older* memory to the front reorders
# anything.
#
# Run it with a memory about metric units stored, which is this project's
# canonical example, or the middle turn overlaps nothing and this is the useless
# version again.
# **The relevance floor (#133) changed what this has to cover, and the turn list
# was incomplete again for the same reason it was the first time.**
#
# The floor drops a memory that neither overlaps the message nor is a standing
# fact. So on an ordinary turn the block is now just the standing facts, which do
# not vary with the message at all, and the five turns above are all ordinary: they
# would report a perfect prefix reuse and say nothing about the floor. Turn 3
# overlaps the metric memory, but that memory is standing and was in the block
# already, so admitting it by overlap changes nothing.
#
# What varies now is a turn that overlaps a *topical* memory, because that memory
# was absent on the turn before and present on this one. The last turn does that
# against the rowing club memory, which is topical and which the floor excludes
# from every other turn here.
#
# So this list now measures two different things and the distinction matters when
# reading the output: turns 1 to 5 are the ordering guarantee, which must still
# hold, and turn 6 is the cost the floor introduced, which is expected to miss.
turns=(
  "What is a good way to keep track of small repairs around the house?"
  "The back gate sticks whenever the wood swells."
  "Is it worth switching from metric units to imperial for baking?"
  "What time do most libraries close on a Sunday?"
  "Remind me what we were talking about."
  "Is the rowing club open on Sunday?"
)

"$adb" logcat -c >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8

# One conversation, held. Not session.sh, because this needs the log cleared and
# read at a specific point rather than screenshots.
# Back, then check we are still in the app.
#
# On the chat list, Back leaves Kam AI entirely and lands on the launcher, and
# every tap after that goes to somebody else's home screen. Two runs were lost to
# this before say.sh was taught to refuse to type when the app is not in front.
# The same guard already existed in mode_fit.sh and was never carried across,
# which is its own lesson about fixing a defect in one script and leaving its
# copies alone.
if "$adb" shell dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 1
fi
"$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 2
if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
  "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
  sleep 6
fi
"$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
"$adb" shell input tap 143 2270
sleep 2
"$adb" shell input tap 160 2122
sleep 7

i=0
for t in "${turns[@]}"; do
  i=$((i+1))
  if ! WAIT=200 ./tools/say.sh "$t" 200 >/dev/null; then
    echo "Aborting at turn $i: could not send, so the prefill counts would be wrong." >&2
    exit 1
  fi
  capture_or_note tools "$out/$(printf '%02d' $i).png"

  # Read the perf line for *this* turn now, rather than reading the whole buffer once
  # at the end. logcat is circular, and a six turn run of long generations aged the
  # earliest lines out: six turns produced five lines, one of which was the titler, so
  # two turn lines were missing and the mapping from line to turn was guesswork.
  #
  # A missing line is also recorded rather than skipped, because "no perf line for turn
  # 4" is a fact about the run and an absence read as nothing is how the thermal
  # measurement went wrong.
  line="$("$adb" logcat -d -s KamPerf 2>/dev/null | grep -oE 'mode=[A-Z]+ TTFT=[0-9]+ms prefill=[0-9]+tok' | tail -1 || true)"
  if [ -z "$line" ]; then
    echo "  turn $i: NO PERF LINE, so this turn is not measured" >&2
    EVIDENCE_MISSES=$((EVIDENCE_MISSES + 1))
  else
    printf '  turn %s  %s\n' "$i" "$line"
  fi
  printf '%s\t%s\n' "$i" "${line:-MISSING}" >> "$out/perf.tsv"
  "$adb" logcat -c >/dev/null 2>&1 || true
done

echo
echo "== prefill tokens per turn, in order =="
echo "   a reused prefix is tens of tokens; a missed one is the whole block"
echo "   read per turn rather than from one buffer dump, so each line belongs to a"
echo "   known turn and a missing one says so"
sed 's/^/   turn /' "$out/perf.tsv"
echo
echo "   mode=BENCH is the conversation titler, which runs after the first exchange of"
echo "   a new chat and uses its own prompt. Expected, not an anomaly."
echo
echo "captures in $out"

# Fails the run if any capture or send went missing. The steps ran; without the
# evidence that they ran as described, this is not a pass.
evidence_exit
