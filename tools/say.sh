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
# A ceiling, not a duration. See the wait at the end of this file.
wait_s="${2:-25}"

# How many generations have finished so far. The engine logs one KamPerf line
# carrying decode= when a generation ends, so this counts completions rather
# than guessing at them.
completions() {
  "$adb" logcat -d -s KamPerf 2>/dev/null | grep -c 'decode=' || true
}
before="$(completions)"

# adb input text swallows everything after a bare space.
escaped="$(printf '%s' "$text" | sed 's/ /%s/g; s/'"'"'/\\'"'"'/g')"

"$adb" shell input tap 363 2263        # the composer
sleep 1

# Empty the composer before typing into it.
#
# Composer text survives, and it survives into a *new* conversation, so anything
# left behind is silently prepended to the next message sent by anything. A run
# stopped part way left "My team keeps missing deadlines and I do not know where"
# sitting there, and the next message went to the model as that fragment glued to
# the front of it. The model answered the combined text perfectly sensibly, which
# is why this is worth guarding rather than watching for: nothing looks wrong.
# The reply was on record as an answer to a question nobody asked.
"$adb" shell input keycombination 113 29 >/dev/null 2>&1 || true   # ctrl+a
sleep 1
"$adb" shell input keyevent 67 >/dev/null 2>&1 || true             # delete
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

# Wait for the reply to finish, rather than for a number of seconds.
#
# This used to be `sleep "$wait_s"`, and a forty-conversation evaluation of
# Brainstorm is what exposed it. Brainstorm answers at length, fifty seconds was
# not enough, and the captures came out mid-sentence: "Let's", "I'll start",
# "Let's examine the core concept of your product name, and then". Several were
# still spinning. Nothing failed, so the run reported forty replies and produced
# a set of screenshots that could not be graded, which is the expensive kind of
# broken: it looks like data.
#
# Raising the number would only move the cliff, because reply length varies with
# the message. So this waits for the engine to say it has finished.
#
# Two generations can follow one message and both must be waited out. The reply
# guard rejects a draft and regenerates, and the conversation titler runs after
# the first reply of a new chat. So finishing once is not enough: this waits for
# a completion and then for six quiet seconds with no further completion, which
# covers a retry or a title starting a moment later.
deadline=$((SECONDS + wait_s))
seen=0
quiet=0
while [ "$SECONDS" -lt "$deadline" ]; do
  sleep 3
  now="$(completions)"
  if [ "$now" -gt "$before" ]; then
    before="$now"
    seen=1
    quiet=0
  elif [ "$seen" -eq 1 ]; then
    quiet=$((quiet + 3))
    [ "$quiet" -ge 6 ] && break
  fi
done
# The screen is a frame or two behind the log line that says the work is done.
sleep 2
[ "$seen" -eq 1 ] || echo "say.sh: no reply completed within ${wait_s}s" >&2
