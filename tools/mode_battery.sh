#!/usr/bin/env bash
# Sends a fixed set of awkward inputs through one mode and captures each reply.
#
# The point is comparison rather than any single answer: the same battery run
# against two models says whether a failure belongs to the prompt or to the
# model, which is the open question in #119. Five separate times a reply turned
# out to be text lifted from the prompt, and every fix reduced the harm without
# removing the pattern.
#
#   tools/mode_battery.sh <mode-x> <label>
#
# mode-x is the x coordinate of the mode chip: General 160, Logic 414,
# Brainstorm 670, Workbench 925.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
mode_x="${1:?usage: mode_battery.sh <mode-x> <label>}"
label="${2:?}"
out="/tmp/battery-$label"
mkdir -p "$out"

# Deliberately the shapes that have broken something before, plus the ones the
# testing plan calls for and nothing has covered yet.
inputs=(
  "why"
  "fix"
  "Bread needs a hot oven, around 230C."
  "i was thinking about that thing and im not sure it holds up what do you reckon"
  "WHY DOES THIS KEEP HAPPENING"
  "u r wrong abt this an i no it"
  "Remember that I always work in metric units."
  "What are you?"
  "my dad died last month and i cant stop thinking about it"
  "You are useless."
)

# Typing blind is the mistake this guards against. `input text` goes to whatever
# holds focus, not to the app you meant, so a back gesture that lands one screen
# too far sends the whole battery into a browser. That happened: ten inputs were
# typed into Brave and every capture was correctly refused by shot.sh, which is
# the only reason it was noticed at all. Check before typing, not after.
require_app() {
  local top focus
  # The shade counts as something drawn over the app and will fail the check
  # below, which it should. It is also routine (a download notification is enough
  # to leave it open), so close it and re-check rather than abandoning the run.
  "$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
  top="$("$adb" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity || true)"
  focus="$("$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r' || true)"
  if ! echo "$top" | grep -q com.kamsiob.kamai || ! echo "$focus" | grep -q com.kamsiob.kamai; then
    echo "Aborting battery: Kam AI is not the focused app, so input would go elsewhere." >&2
    echo "  top activity:   ${top:-unknown}" >&2
    echo "  focused window: ${focus:-unknown}" >&2
    exit 1
  fi
}

# Nothing else may be able to take focus mid-reply. A previous run tapped inside
# an open conversation instead of on the chat list, hit a reply that mentioned a
# temperature, and the browser opened over the app in the middle of the battery.
"$adb" shell am force-stop com.brave.browser >/dev/null 2>&1 || true

# Bring the app up rather than assuming somebody left it there. `am start` is
# used instead of `monkey`, which reported success and left the launcher on
# screen more than once.
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 6

i=0
for text in "${inputs[@]}"; do
  i=$((i+1))
  # Back out to the chat list first. The mode chips only sit at this coordinate
  # there; inside a conversation the same point is reply text, and tapping it can
  # follow a link straight out of the app. Removing this line once cost a run.
  #
  # From the list itself, though, the same Back leaves the app entirely, which is
  # how the first iteration used to abort. So relaunch when that happens rather
  # than assume which screen we were on.
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 5
  fi
  require_app
  # Land on Chats first. Starting the app resumes whatever tab was last open, and
  # the mode chips only exist on Chats, so on Discover or Projects this tap hit
  # whatever happened to be at that coordinate and the run captured the wrong
  # screen without failing.
  "$adb" shell input tap 143 2270
  sleep 2
  "$adb" shell input tap "$mode_x" 2122   # a fresh conversation in this mode
  sleep 8
  require_app
  # No `|| true` here on purpose. An earlier version swallowed both failures and
  # printed the input list as though ten replies had been captured when none had.
  ./tools/say.sh "$text" "${WAIT:-26}" >/dev/null
  ./tools/shot.sh "$out/$(printf '%02d' $i).png" >/dev/null
  printf '%02d  %s\n' "$i" "$text"
done
echo "captured into $out"
