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
"$adb" shell input keyevent KEYCODE_BACK   # drop the keyboard so send is where we expect
sleep 1
"$adb" shell input tap 998 2263        # send
sleep "$wait_s"
