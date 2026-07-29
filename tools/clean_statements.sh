#!/usr/bin/env bash
# Does this model restate declarative statements, or does one input collide with
# one example?
#
#   tools/clean_statements.sh <label> [repeats]
#
# #122 has always been measured on "Bread needs a hot oven, around 230C." The
# hard rules carry a worked example about working in metric units, answered with
# a line about keeping to metric, and one of the earliest failures on record was
# that metric answer appearing on an unrelated input. Bread, ovens and 230C sit
# in the same semantic neighbourhood as units and measurement, so every
# measurement taken on that input has been testing two hypotheses at once:
#
#   general    this model restates declarative statements
#   collision  this one input interferes with that one example
#
# They call for opposite responses. The general hypothesis means the instruction
# needs work. The collision means the *test input* needs changing and the prompt
# is fine, which would also explain why five rounds of prompt editing failed and
# why this input alone failed identically on both model tiers when nothing else
# did.
#
# So: four statements structurally identical to the bread input, each a bare
# declarative carrying its own obvious follow-on, and each chosen to share no
# subject matter with anything in any prompt. No units, no measurement, no
# temperature, no cooking, and nothing resembling a preference the memory feature
# would store. The bread input runs alongside them rather than being replaced,
# because the comparison is the finding.
set -euo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/lib/loud.sh"
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

label="${1:?usage: clean_statements.sh <label> [repeats]}"
repeats="${2:-3}"
out="/tmp/clean-$label"
log="/tmp/clean-$label.log"
mkdir -p "$out"

# The contaminated one, kept for comparison.
BREAD="Bread needs a hot oven, around 230C."

# The clean ones. Deliberately dull, domestic, and nowhere near the prompts:
# nothing about units, storage, installs, buildings, dates or preferences.
CLEAN_1="The back gate sticks whenever the wood swells."
CLEAN_2="The library closes early on Sundays."
CLEAN_3="Sparrows have started nesting in the porch roof."
CLEAN_4="The photocopier jams whenever the paper is damp."

prior_rotation="$("$adb" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
restore() {
  [ "${prior_rotation:-1}" = "1" ] && \
    "$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
}
trap restore EXIT
"$adb" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$adb" shell settings put system user_rotation 0 >/dev/null 2>&1 || true

"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8

run_one() {  # id text
  local id="$1" text="$2" i echoes therm
  for i in $(seq 1 "$repeats"); do
    "$adb" logcat -c >/dev/null 2>&1 || true
    "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 2
    if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
      "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
      sleep 5
    fi
    "$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
    "$adb" shell input tap 143 2270       # Chats
    sleep 2
    "$adb" shell input tap 160 2122       # General
    sleep 7
    # No `|| true`. A send that fails means nothing was measured, and this
    # script used to report clean runs having sent nothing.
    if ! WAIT=200 ./tools/say.sh "$text" 200 >/dev/null; then
      echo "Aborting: could not send \"$id\" run $i. Nothing after this is data." >&2
      exit 1
    fi
    if ! ./tools/shot.sh "$out/$id-$i.png" >/dev/null; then
      echo "Aborting: could not capture \"$id\" run $i, so the reply cannot be read." >&2
      exit 1
    fi
    echoes="$(adb_count "rejected replies" 'rejected' "$adb" logcat -d -s KamEcho)"
    therm="$("$adb" shell dumpsys thermalservice 2>/dev/null |
      grep -m1 'Thermal Status:' | grep -oE '[0-9]+' || echo '?')"
    printf '%s\t%s\t%s\t%s\n' "$id" "$i" "$echoes" "${therm:-?}" >> "$log"
    printf '  %-8s run %s  echo=%s\n' "$id" "$i" "$echoes"
  done
}

: > "$log"
echo "== declarative statements, General, $repeats runs each =="
run_one bread "$BREAD"
run_one gate "$CLEAN_1"
run_one library "$CLEAN_2"
run_one sparrows "$CLEAN_3"
run_one copier "$CLEAN_4"

echo
echo "== rejections per input =="
awk -F'\t' '{e[$1]+=$3; n[$1]++} END { for (k in n) printf "%-9s %d runs  %d rejections\n", k, n[k], e[k] }' "$log" | sort
echo "rows in $log, captures in $out"
