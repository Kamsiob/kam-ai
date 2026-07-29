#!/usr/bin/env bash
# How often does this phone actually reach thermal LIGHT?
#
#   tools/thermal_frequency.sh [samples] [interval-seconds]
#
# This exists to answer one open question on #134, and it is worth stating the
# question precisely, because the obvious reading of it is wrong.
#
# `warningMessage()` had no callers, so nothing was said at any thermal level while
# context silently shrank at MODERATE. That is a real defect and it is fixed. What
# the fix does not settle is whether LIGHT should speak. An earlier measurement had
# the phone reaching LIGHT and shedding it again within ninety seconds of idling,
# skin temperature 40.3 falling to 37.1. If LIGHT is that common and that brief in
# ordinary use, a sentence every time is noise, and noise about performance makes an
# application feel worse than silence does. So:
#
#   frequent and brief  adapt silently at LIGHT, speak from MODERATE up
#   rare                speaking at LIGHT is right
#
# The fact that a code path existed with no caller does not decide it. A measurement
# does.
#
# **This script cannot answer the question on its own, and that is important.** It
# polls, and polling adds no load. An idle phone sits at status 0 indefinitely: a
# run of 200 samples against a phone doing nothing produced 200 zeroes, which is not
# evidence that LIGHT is rare, only evidence that nothing was running. The question
# is about ordinary use, so the sampling has to overlap ordinary use: either the
# owner using the application normally, or a generation battery running alongside.
# A quiet run is not a result.
#
# Read-only throughout. It changes nothing on the device.
set -euo pipefail
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
serial="${KAMAI_SERIAL:-57241FDCQ0000H}"   # the phone appears twice in adb devices

samples="${1:-200}"
interval="${2:-20}"
out="${KAMAI_THERMAL_LOG:-/tmp/thermal-freq.tsv}"

printf 'sample\tstatus\tbattery_c\n' > "$out"
echo "sampling $samples times every ${interval}s into $out"
echo "a run against an idle phone will be all zeroes and answers nothing"

for i in $(seq 1 "$samples"); do
  s="$("$adb" -s "$serial" shell dumpsys thermalservice 2>/dev/null |
       grep -m1 'Thermal Status:' | grep -oE '[0-9]+' || echo '?')"
  t="$("$adb" -s "$serial" shell dumpsys battery 2>/dev/null |
       grep -m1 'temperature' | grep -oE '[0-9]+' || echo '?')"
  printf '%s\t%s\t%s\n' "$i" "${s:-?}" "${t:-?}" >> "$out"
  sleep "$interval"
done

echo
echo "== how often each level occurred =="
awk -F'\t' 'NR>1 {c[$2]++; n++} END {
  for (k in c) printf "  status %s  %4d samples  %5.1f%%\n", k, c[k], 100*c[k]/n
}' "$out" | sort
echo
echo "0 NONE, 1 LIGHT, 2 MODERATE, 3 SEVERE. If every sample is 0, the phone was"
echo "idle and this run is not evidence about ordinary use."
