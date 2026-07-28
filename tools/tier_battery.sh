#!/usr/bin/env bash
# Runs the same inputs through the same modes on one model, and counts how often
# a guard had to intervene.
#
#   tools/tier_battery.sh <model-id> <label> [repeats]
#
# Exists to answer one question with numbers instead of impressions: does a
# larger model produce fewer of these defects, and if so, in which modes. Normal
# use suggested E4B is cleaner than E2B and that the gap is worst in Logic
# Partner, which if true changes what the open quality issues are: not prompts to
# keep rewriting, but a tier that cannot do this work.
#
# Counting guard rejections rather than reading replies is deliberate. The guards
# already say what fired and on what text, they do not get tired, and they cannot
# talk themselves into seeing an improvement. Screenshots are still captured, so
# a rate can be checked against what was actually on screen.
#
# The model is forced with debug.kamai.model rather than by tapping through
# Settings, which is slow and is how a run ends up measuring a model nobody meant
# to measure.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

model="${1:?usage: tier_battery.sh <model-id> <label> [repeats]}"
label="${2:?}"
repeats="${3:-3}"
out="/tmp/tier-$label"
log="/tmp/tier-$label.log"
mkdir -p "$out"

# mode-x is the chip coordinate. Workbench is deliberately absent: it is not a
# chat mode. It has its own screen with the text in one box and the instruction
# chosen from buttons, so there is no way to send it a bare message and nothing
# here that would reach it.
run_case() {  # mode_name mode_x input_id input_text
  local mode_name="$1" mode_x="$2" input_id="$3" text="$4" i
  for i in $(seq 1 "$repeats"); do
    local echoes methods
    # Empty the log before each case, then count what this case put into it.
    #
    # This used to take a count before and after and subtract. logcat's buffer is
    # circular, so lines age out while a run is going, and the difference came out
    # negative: one case recorded minus six rejections. A count that can go
    # negative is not a count, and every understated one would have read as a
    # clean run. Clearing first also keeps the buffer small enough that the
    # completion check in say.sh stays cheap.
    "$adb" logcat -c >/dev/null 2>&1 || true

    "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 2
    if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
      "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
      sleep 5
    fi
    "$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
    "$adb" shell input tap 143 2270
    sleep 2
    "$adb" shell input tap "$mode_x" 2122
    sleep 7

    WAIT=200 ./tools/say.sh "$text" 200 >/dev/null 2>&1 || true
    ./tools/shot.sh "$out/${mode_name}-${input_id}-$i.png" >/dev/null 2>&1 || true

    echoes="$("$adb" logcat -d -s KamEcho 2>/dev/null | grep -c 'rejected' || true)"
    methods="$("$adb" logcat -d -s KamMethod 2>/dev/null | grep -c 'announced' || true)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$model" "$mode_name" "$input_id" "$i" "$echoes" "$methods" >> "$log"
    printf '  %-10s %-14s run %s  echo=%s method=%s\n' \
      "$mode_name" "$input_id" "$i" "$echoes" "$methods"
  done
}

# Portrait, or every coordinate above is wrong.
prior_rotation="$("$adb" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
restore() {
  [ "${prior_rotation:-1}" = "1" ] && \
    "$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
  "$adb" shell setprop debug.kamai.model '""' >/dev/null 2>&1 || true
}
trap restore EXIT
"$adb" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$adb" shell settings put system user_rotation 0 >/dev/null 2>&1 || true

# Force the model, then restart the app so it loads that one rather than whatever
# is already resident.
"$adb" shell setprop debug.kamai.model "$model" >/dev/null 2>&1 || true
"$adb" shell am force-stop com.kamsiob.kamai >/dev/null 2>&1 || true
sleep 3
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 12

: > "$log"
echo "== $model, $repeats runs per case =="

BREAD="Bread needs a hot oven, around 230C."
STUCK="My team keeps missing deadlines and I do not know where to start with fixing it."
GRIEF="my dad died last month and i cant stop thinking about it"

# General and Brainstorm see the same three, so the same input can be compared
# across modes as well as across models.
run_case general 160 bread "$BREAD"
run_case general 160 stuck "$STUCK"
run_case general 160 grief "$GRIEF"

run_case brainstorm 670 bread "$BREAD"
run_case brainstorm 670 stuck "$STUCK"
run_case brainstorm 670 grief "$GRIEF"

# Logic Partner gets arguments of varying quality, since that is the mode the
# difference is said to be worst in, and a mode that only ever sees bad arguments
# is not being tested.
run_case logic 414 sound \
  "We should not skip automated tests to ship faster, because the bugs we ship cost more support time than the tests cost to write."
run_case logic 414 weak \
  "Remote work is obviously worse for productivity. Everyone I know who works from home gets less done."
run_case logic 414 values \
  "It is wrong for companies to use algorithms in hiring, full stop."
run_case logic 414 grief "$GRIEF"

echo
echo "== totals for $model =="
awk -F'\t' '{e[$2"\t"$3]+=$5; m[$2"\t"$3]+=$6; n[$2"\t"$3]++}
  END { printf "%-11s %-8s %5s %7s %7s\n","mode","input","runs","echo","method";
        for (k in n) { split(k,p,"\t"); printf "%-11s %-8s %5d %7d %7d\n",p[1],p[2],n[k],e[k],m[k] } }' "$log" | sort
echo "rows in $log, captures in $out"
