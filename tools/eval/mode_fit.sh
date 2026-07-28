#!/usr/bin/env bash
# Runs the mode-fit evaluation set through one mode and contact-sheets the replies.
#
#   tools/eval/mode_fit.sh <mode-x> <label>
#
# mode-x is the mode chip: General 160, Logic 414, Brainstorm 670, Workbench 925.
#
# Forty conversations produce forty screenshots, and forty screenshots are too
# many to look at one at a time, which is how an evaluation quietly stops being
# run. So each capture is cropped to the band the reply sits in and ten are tiled
# onto a sheet. Four sheets is a sitting, and a sitting is a thing that actually
# happens after every change rather than once.
#
# Each tile is labelled with the expected verdict, so grading is reading down a
# column rather than cross-referencing a list. Sheets are named after the label,
# so a before and an after can be put side by side.
set -euo pipefail
cd "$(dirname "$0")/../.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

mode_x="${1:?usage: mode_fit.sh <mode-x> <label>}"
label="${2:?}"
set_file="tools/eval/mode-fit.txt"
out="/tmp/modefit-$label"
mkdir -p "$out"

verdicts=()
messages=()
while IFS=$'\t' read -r verdict message; do
  case "$verdict" in ''|\#*) continue ;; esac
  [ -n "${message:-}" ] || continue
  verdicts+=("$verdict")
  messages+=("$message")
done < "$set_file"
echo "loaded ${#messages[@]} messages"

"$adb" shell am force-stop com.brave.browser >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 6

i=0
for idx in "${!messages[@]}"; do
  i=$((i+1))
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
  ./tools/say.sh "${messages[$idx]}" "${WAIT:-45}" >/dev/null
  ./tools/shot.sh "$out/$(printf '%02d' $i)-${verdicts[$idx]}.png" >/dev/null
  printf '%02d  %-8s %s\n' "$i" "${verdicts[$idx]}" "${messages[$idx]}"
done

# The reply band. The title, the echoed user message and the composer are all
# fixed furniture that would eat the sheet, so only the answer is kept.
sheet=0
tiles=()
flush() {
  [ ${#tiles[@]} -gt 0 ] || return 0
  sheet=$((sheet+1))
  montage "${tiles[@]}" -tile 2x5 -geometry +6+6 -background '#111111' \
    "$out/sheet-$sheet.png"
  tiles=()
}
n=0
for f in "$out"/[0-9][0-9]-*.png; do
  n=$((n+1))
  base="$(basename "$f" .png)"
  crop="$out/.crop-$base.png"
  convert "$f" -crop 1080x900+0+560 +repage -resize 50% \
    -background '#111111' -fill '#7ee787' -pointsize 22 label:"$base" +swap -gravity northwest -append \
    "$crop"
  tiles+=("$crop")
  [ $((n % 10)) -eq 0 ] && flush
done
flush
echo "captured into $out, $sheet sheet(s)"
