#!/usr/bin/env bash
# Types a message into the open chat and sends it, then waits for the reply.
#
# Exists because driving this by hand costs a screenshot and three taps per
# message, and testing against real messiness needs dozens of messages rather
# than three. The keyboard is dismissed before the send tap on purpose: with it
# up the send button sits at a different height and the tap lands on the emoji
# key instead, which cost two wasted runs before it was made deterministic.
set -euo pipefail
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
text="${1:?usage: say.sh <message> [wait-seconds]}"
wait_s="${2:-25}"

# adb input text swallows everything after a bare space.
escaped="$(printf '%s' "$text" | sed 's/ /%s/g; s/'"'"'/\\'"'"'/g')"

"$adb" shell input tap 363 2263        # the composer
sleep 1
"$adb" shell input text "$escaped"
sleep 1

# Drop the keyboard so send is where we expect, but only when it is actually up.
#
# This used to press Back unconditionally. When the keyboard had not opened, that
# Back was delivered to the app instead: it popped the conversation, and on the
# chat list it left the app altogether. The run continued typing into the
# launcher. Seen twice, and both times the symptom was a battery that stopped
# part way with no error from this script.
if "$adb" shell dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then
  "$adb" shell input keyevent KEYCODE_BACK
  sleep 1
fi

# The composer must still be there. If Back or anything else moved us, sending
# would tap whatever is now at that coordinate.
if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
  echo "say.sh: Kam AI is no longer focused, refusing to tap send" >&2
  exit 1
fi

"$adb" shell input tap 998 2263        # send
sleep "$wait_s"
