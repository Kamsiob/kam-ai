#!/usr/bin/env bash
# Holds one conversation across many turns, as a person would.
#
#   tools/session.sh <mode-x> <label> <<'MSGS'
#   first message
#   second message
#   MSGS
#
# mode-x is the mode chip: General 160, Logic 414, Brainstorm 670, Workbench 925.
#
# Exists because mode_battery.sh opens a fresh chat per input, which tests first
# impressions and nothing else. Every defect found so far has been a first-turn
# defect, which is partly a fact about the app and partly a fact about how it was
# being tested. Drift, contradiction, forgetting what was said three turns ago and
# holding a position under pressure are all multi-turn behaviours, and none of
# them can appear in a set of one-turn conversations.
#
# Captures after every turn, so the transcript can be read as it grew rather than
# only at the end.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

mode_x="${1:?usage: session.sh <mode-x> <label> < messages}"
label="${2:?}"
wait_s="${WAIT:-45}"
out="/tmp/session-$label"
mkdir -p "$out"

# Read every message first, before anything else runs. Any command that inherits
# stdin will eat it, and adb does: the first version read after starting the app
# and the taps, so the here-document was gone by the time the loop looked, and
# the run reported success having sent nothing at all.
messages=()
while IFS= read -r line; do
  [ -n "$line" ] && messages+=("$line")
done
if [ ${#messages[@]} -eq 0 ]; then
  echo "session.sh: no messages on stdin" >&2
  exit 1
fi

"$adb" shell am force-stop com.brave.browser >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 6
"$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true

# Get out of any open conversation first, then land on Chats, then open one new
# one. Everything after this stays in it: no Back, no fresh chat, because the
# point is the conversation.
#
# Without the Back, starting the app resumed into whatever conversation was last
# open and the whole session was appended to it, under its title and after its
# history. The transcript then read as one conversation that changed subject
# halfway, which is a confusing thing to be handed as evidence.
"$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 2
if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
  "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
  sleep 5
fi
"$adb" shell input tap 143 2270
sleep 2
"$adb" shell input tap "$mode_x" 2122
sleep 7

i=0
for line in "${messages[@]}"; do
  i=$((i+1))
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    echo "Aborting at turn $i: Kam AI is not focused." >&2
    exit 1
  fi
  ./tools/say.sh "$line" "$wait_s" >/dev/null
  ./tools/shot.sh "$out/$(printf '%02d' $i).png" >/dev/null
  printf '%02d  %s\n' "$i" "$line"
done

echo "captured into $out"
