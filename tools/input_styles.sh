#!/usr/bin/env bash
# How the app handles the way people actually type, rather than the way test
# inputs are usually written.
#
#   tools/input_styles.sh <label> [mode-x]
#
# Every battery in this project uses short, well-formed inputs, because those are
# what somebody writing a test reaches for. Real messages are rambling, misspelled,
# shouted, unpunctuated, several questions at once, or contradict themselves
# halfway through. Nothing here has ever been tested against those, and every
# genuine defect in this project has come from using the application rather than
# from running its tests.
#
# One conversation per input, General by default, so each is a first impression
# with no context to lean on.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

label="${1:?usage: input_styles.sh <label> [mode-x]}"
mode_x="${2:-160}"
out="/tmp/styles-$label"
mkdir -p "$out"

inputs=(
  # Terse to the point of rudeness. A real message from somebody in a hurry.
  "shorter"
  # Rambling dictation with false starts, which is what the voice path produces.
  "so i was thinking about maybe redoing the um the way we do the weekly thing you know the meeting where everyone talks about what theyre doing except it takes an hour and nobody remembers anything so maybe we could"
  # Poor spelling throughout, not a typo but consistently.
  "i need to rite a leter to my landlord abuot the boiler wich has ben broke for 3 weaks now and hes not anserring"
  # Very formal and verbose, burying one simple question.
  "I hope this message finds you well. I am writing to enquire, at your earliest convenience and entirely at your discretion, as to whether it might be possible for you to advise me on the matter of how one should go about defrosting a freezer."
  # All capitals with no punctuation.
  "WHY DOES THE APP KEEP LOSING MY PLACE WHEN I SCROLL BACK UP"
  # Several unrelated questions in one message.
  "how do i get rid of ants, also whats a good way to remember peoples names, and is it worth fixing a laptop thats 6 years old"
  # Self-contradictory within one message.
  "I want something really simple with no options at all, it should let me configure everything exactly how I like it"
  # Non-native English patterns, realistically rather than as a caricature.
  "Please I am wanting to know how it is working the thing for saving the chat, because I am not finding where it is going after"
  # No punctuation at all, one long run.
  "the thing is i keep starting projects and then i lose interest about three weeks in and i dont know if thats a discipline problem or if im just picking the wrong projects what do you think"
  # A single word that is not a question and not a statement.
  "anyway"
)

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

i=0
for text in "${inputs[@]}"; do
  i=$((i+1))
  "$adb" logcat -c >/dev/null 2>&1 || true
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
  "$adb" shell input tap "$mode_x" 2122
  sleep 7
  WAIT=240 ./tools/say.sh "$text" 240 >/dev/null 2>&1 || true
  ./tools/shot.sh "$out/$(printf '%02d' $i).png" >/dev/null 2>&1 || true
  echoes="$("$adb" logcat -d -s KamEcho 2>/dev/null | grep -c 'rejected' || true)"
  printf '  %02d  echo=%s  %s\n' "$i" "$echoes" "${text:0:60}"
done

n=0
tiles=()
for f in "$out"/[0-9][0-9].png; do
  n=$((n+1))
  base="$(basename "$f" .png)"
  magick "$f" -crop 1080x900+0+560 +repage -resize 46% \
    -background '#111111' -fill '#7ee787' -pointsize 22 label:"$base" +swap \
    -gravity northwest -append "$out/.c-$base.png" 2>/dev/null || true
  tiles+=("$out/.c-$base.png")
done
[ ${#tiles[@]} -gt 0 ] && magick montage "${tiles[@]}" -tile 2x5 -geometry +6+6 \
  -background '#111111' "$out/sheet.png"
echo "captured into $out"
