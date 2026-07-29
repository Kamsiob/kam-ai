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

# The most recently finished generation, as its whole log line. The engine logs
# one KamPerf line carrying decode= when a generation ends.
#
# The last line rather than a count of them. Counting looked simpler and is
# wrong: logcat's buffer is circular, so old lines age out while new ones
# arrive, the total stops rising, and every message then waits out the full
# ceiling. That turned a thirty case battery into an hour of sleeping.
completions() {
  "$adb" logcat -d -s KamPerf 2>/dev/null | grep 'decode=' | tail -1 || true
}
before="$(completions)"

# Refuse to touch the screen at all unless Kam AI is in front.
#
# This check used to sit further down, just before the send tap, which meant the
# composer tap, a select-all, a delete and the typing had all already happened by
# the time anything looked. A run once drove that sequence into the phone owner's
# calendar because the app was not where it was assumed to be. Nothing was lost,
# since those coordinates hit no editable field there, and that was luck rather
# than design: a select-all followed by a delete is a destructive pair to send at
# an unknown application.
#
# The capture guard in shot.sh caught the same run and refused to photograph
# anything, which is why there were no screenshots of somebody's calendar. This
# closes the earlier half of the same hole.
if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
  echo "say.sh: Kam AI is not in front, refusing to type anything" >&2
  exit 1
fi

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
  if [ "$now" != "$before" ]; then
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

# **Exit nonzero, do not merely mention it.** This warning used to be the last
# command in the file, so the `echo` succeeded and the script exited 0 whatever
# had happened. Callers were then told a reply had not arrived and carried on as
# though one had, which is the same class as the four scripts fixed in 964c413:
# mode_battery.sh deliberately has no `|| true` on this call, and that care was
# defeated by the exit code.
#
# Found by tools/memory_floor_probe.sh, where the first message of a run timed out
# on a cold model, the run continued, and the capture happened to prove the reply
# had in fact landed a moment later. Benign that time. Not a reason to leave it.
#
# A caller that genuinely wants to continue past a timeout has to say so with an
# explicit `|| true` and a reason. The usual fix is a larger ceiling: the first
# generation after the app starts loads five gigabytes of weights and is much
# slower than every one after it.
if [ "$seen" -ne 1 ]; then
  echo "say.sh: no reply completed within ${wait_s}s" >&2
  echo "say.sh: the message may still have been sent; raise the ceiling for a cold model" >&2
  exit 1
fi
