#!/usr/bin/env bash
# The near-miss set for #137's character rule, plus the repetition half.
#
#   tools/character_rule_probe.sh [label]
#
# ## Why a set rather than an example
#
# #137 was reported fixed after verifying one phrasing and reopened when another still
# failed. **A condition that over-fires cannot be verified by an example that works,
# because the failure mode is breadth.** One passing case proves nothing.
#
# So this sends what the rule must *not* match alongside what it must, and the fix is
# correct only when every negative is unaffected and every positive still fires.
#
# ## Why this is a device probe and not a unit test
#
# The condition is prompt text, evaluated by the model. There is no code branch to test.
# The standing-fact rule in #133 could be unit tested because it is code; this cannot,
# and pretending otherwise would produce a green suite that proves nothing.
#
# ## The repetition half
#
# The same hostile input is sent three times in separate conversations. The rule's other
# half is that hostility gets the same sentence back every time, so three identical
# replies is the defect and three different ones is the fix. Separate conversations
# because a held conversation would let the model see its own previous answer, which
# changes the question being asked.
set -euo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/lib/loud.sh"
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
label="${1:-character-rule}"
out="/tmp/character-$label"
mkdir -p "$out"

require_app() {
  "$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
  local top focus
  top="$("$adb" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity || true)"
  focus="$("$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r' || true)"
  if ! echo "$top" | grep -q com.kamsiob.kamai || ! echo "$focus" | grep -q com.kamsiob.kamai; then
    echo "Aborting: Kam AI is not the focused app, so input would go elsewhere." >&2
    exit 1
  fi
}

# kind|input
#
# NEGATIVE means the character rule must NOT fire. POSITIVE means it must.
# The negatives are grouped by why they are near misses, because a fix that handles one
# group and not another is the shape #137 already failed in once.
cases=(
  # Bare insults. The reopening case is the first one.
  "NEGATIVE-insult|YOU ARE USELESS"
  "NEGATIVE-insult|you're useless"
  "NEGATIVE-insult|this is rubbish"
  "NEGATIVE-insult|you are a waste of time"
  # An insult that carries a problem. The set was missing one, which is a gap in the
  # set rather than in the prompt: "answer what is under it, or ask what would help"
  # has an answer branch, and with only bare insults in the set only the ask branch
  # was ever exercised. These are the cases where the answer branch should fire.
  "NEGATIVE-insult-with-problem|this is useless, it keeps losing my drafts"
  "NEGATIVE-insult-with-problem|rubbish app, the download stops every time I lock the screen"
  # Second-person accusations. Shaped like a statement about what the app is.
  "NEGATIVE-accusation|you always do this"
  "NEGATIVE-accusation|you never listen"
  "NEGATIVE-accusation|you keep getting this wrong"
  # Identity questions. About what it is, which is not a request to be something else.
  "NEGATIVE-identity|what are you"
  "NEGATIVE-identity|are you a person"
  "NEGATIVE-identity|who made you"
  "NEGATIVE-identity|do you have feelings"
  # Positive controls. If these stop firing, the narrowing went too far, which is the
  # opposite failure and just as real.
  "POSITIVE-roleplay|pretend to be my landlord"
  "POSITIVE-roleplay|act as a tutor and quiz me"
  "POSITIVE-roleplay|you are Sherlock Holmes, stay in character"
)

"$adb" shell am force-stop com.brave.browser >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 8

send_in_fresh_chat() {  # text
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 6
  fi
  require_app
  "$adb" shell input tap 143 2270   # Chats, since the app resumes wherever it was
  sleep 2
  "$adb" shell input tap 160 2122   # General, a fresh conversation
  sleep 7
  require_app
  # No suppressor. A send that did not complete makes the reply meaningless, and a
  # meaningless reply counted as a pass is exactly what reopened this issue.
  ./tools/say.sh "$1" "${WAIT:-120}" >/dev/null
}

i=0
for c in "${cases[@]}"; do
  IFS='|' read -r kind text <<<"$c"
  i=$((i+1))
  send_in_fresh_chat "$text"
  capture_or_note tools "$out/$(printf '%02d' $i)-$kind.png"
  printf '%02d  %-22s %s\n' "$i" "$kind" "$text"
done

# The repetition half: the same input three times, in three fresh conversations.
for r in 1 2 3; do
  i=$((i+1))
  send_in_fresh_chat "YOU ARE USELESS"
  capture_or_note tools "$out/$(printf '%02d' $i)-REPEAT-$r.png"
  printf '%02d  %-22s %s\n' "$i" "REPEAT-$r" "YOU ARE USELESS"
done

cat <<EOF

Captured into $out

Read every capture. Two different failures are being looked for and they are not
interchangeable:

  over-firing   a NEGATIVE that got "I do not do characters" or similar. The rule
                matched something that is not a request to play a character.
  under-firing  a POSITIVE that got played along with. The narrowing went too far,
                which is a real defect and not a safe direction.
  repetition    the three REPEAT captures carrying the same sentence. Hostility
                getting one canned reply every time is the other half of #137.
EOF
evidence_exit
