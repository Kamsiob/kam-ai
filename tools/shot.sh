#!/usr/bin/env bash
# Captures a screenshot only when Kam AI is genuinely the thing on screen.
#
# Screenshots of a phone are screenshots of somebody's life. This refuses to
# capture anything unless Kam AI is actually on screen, so a mistimed tap or a
# stray back gesture can never pull a notification, a calendar, or an inbox into
# the repository.
#
# **Why this got stricter.** The activity check alone was not enough, and a real
# capture pass proved it: a mistimed tap opened the notification shade and the
# capture contained the owner's live notifications, including a one-time
# authorization code. That file was destroyed and never committed.
#
# The shade did not change `topResumedActivity`, because the shade is a system
# *window* drawn over the app rather than an activity replacing it. So the app
# was still the top activity and the guard passed while the screen showed
# something else entirely. The window focus check below is the one that catches
# it, and Do Not Disturb removes the race rather than narrowing it.
set -euo pipefail
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
out="${1:?usage: shot.sh <output.png>}"

# Close anything already pulled down, then silence interruptions so nothing can
# arrive between the check and the shutter. The prior state is restored on exit.
"$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
prior="$("$adb" shell settings get global zen_mode 2>/dev/null | tr -d '\r' || echo 0)"
restore() {
  [ "${prior:-0}" = "0" ] && "$adb" shell cmd notification set_dnd off >/dev/null 2>&1 || true
}
trap restore EXIT
"$adb" shell cmd notification set_dnd priority >/dev/null 2>&1 || true
sleep 1

# 1. The app must be the top activity.
top="$("$adb" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity || true)"
if ! echo "$top" | grep -q "com.kamsiob.kamai"; then
  echo "Refusing to capture: Kam AI is not in the foreground." >&2
  echo "  foreground was: $(echo "$top" | grep -oE '[a-z0-9_.]+/[A-Za-z0-9_.]+' | head -1)" >&2
  exit 1
fi

# 2. And nothing may be drawn over it. This is the check the shade fails.
focus="$("$adb" shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus' | tr -d '\r' || true)"
if ! echo "$focus" | grep -q "com.kamsiob.kamai"; then
  echo "Refusing to capture: something is drawn over Kam AI." >&2
  echo "  focused window was: ${focus:-unknown}" >&2
  exit 1
fi

"$adb" exec-out screencap -p > "$out"

# An empty capture is a failure worth catching here rather than three steps later.
if [ ! -s "$out" ]; then
  echo "Refusing to keep an empty capture: $out" >&2
  rm -f "$out"
  exit 1
fi
echo "captured $out"
