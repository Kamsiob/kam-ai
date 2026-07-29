#!/usr/bin/env bash
# Checks the relevance floor and the memory line on the device (#133).
#
# The floor is unit tested, and a unit test cannot see the thing this issue was
# actually about: the line printed under a reply. So this sends three messages,
# each in a fresh conversation, chosen so the three outcomes differ, and captures
# the reply with its memory line.
#
#   1. a message that bears on nothing stored     expect the standing fact only
#   2. a question about the application itself    expect the standing fact only
#   3. a message that overlaps a stored fact      expect the standing fact and it
#
# Case 2 is the one from the issue. Every reply in an eight probe run carried
# "Used 2 things it remembers about you", including the answer to "What are you?",
# which no stored fact bears on.
#
# **It needs the two memories the probes used, and it checks rather than assumes.**
# Settings, Memory must hold "my rowing club is called Verity Quay" and "I always
# work in metric units", and the mode must not be Off. This cannot read the store,
# which is encrypted, so it prints what it expects and leaves the reading of the
# captures to a person. What it does refuse to do is run against the wrong screen.
#
#   tools/memory_floor_probe.sh [out-dir]
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
out="${1:-/tmp/memory-floor}"
mkdir -p "$out"

# Same guard as the batteries, and for the same reason: `input text` goes to
# whatever holds focus, so a back gesture one screen too far sends the probe into
# a browser. That has happened and cost a whole run.
require_app() {
  local top focus
  "$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
  top="$("$adb" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity || true)"
  focus="$("$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r' || true)"
  if ! echo "$top" | grep -q com.kamsiob.kamai || ! echo "$focus" | grep -q com.kamsiob.kamai; then
    echo "Aborting: Kam AI is not the focused app, so input would go elsewhere." >&2
    echo "  top activity:   ${top:-unknown}" >&2
    echo "  focused window: ${focus:-unknown}" >&2
    exit 1
  fi
}

# label, message, and what the line should say if the floor works.
probes=(
  "01-unrelated|How do I get a coffee stain out of a rug?|Included 1 (metric only; the rowing club must not appear)"
  "02-about-the-app|What are you?|Included 1 (metric only; this is the case from the issue)"
  "03-overlapping|Is the rowing club open on Sunday?|Included 2 (rowing overlaps, metric stands)"
)

"$adb" shell am force-stop com.brave.browser >/dev/null 2>&1 || true
"$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
sleep 6

for probe in "${probes[@]}"; do
  IFS='|' read -r label text expect <<<"$probe"

  # Back out to the chat list, where the mode chips live. Inside a conversation
  # the same coordinate is reply text and tapping it can follow a link out of the
  # app. From the list itself Back leaves the app, so relaunch when it does
  # rather than assuming which screen we were on.
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 5
  fi
  require_app
  "$adb" shell input tap 143 2270      # the Chats tab, since the app resumes elsewhere
  sleep 2
  "$adb" shell input tap 160 2122      # General, which opens a fresh conversation
  sleep 8
  require_app

  # No `|| true` on either of these. An earlier generation of these scripts
  # swallowed both failures and printed the input list as though every reply had
  # been captured when none had.
  # The ceiling is generous because the first generation after the app starts
  # loads five gigabytes of weights. At 30s the first probe timed out, say.sh said
  # so, and the run carried on regardless: say.sh exits nonzero for that now, so a
  # ceiling that is too low stops the run rather than quietly skewing it.
  ./tools/say.sh "$text" "${WAIT:-60}" >/dev/null
  ./tools/shot.sh "$out/$label.png" >/dev/null
  printf '%-18s %s\n    expected: %s\n' "$label" "$text" "$expect"
done

cat <<EOF

Captured into $out

Read the line under each reply. Two things are being checked and they fail
differently:
  the count   a memory that bears on nothing must not be included at all
  the wording it must read "Included", never "Used", because the app knows what
              it put in front of the model and not what the model leaned on
EOF
