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

# Focus arrives before the pixels do.
#
# An activity can hold focus while the screen still shows what was there before
# it, which is the whole of an activity transition. A capture taken in that window
# passes every check above and photographs the previous app. It happened: the
# focus was Kam AI's text intake activity and the image was the phone's assistant
# settings, listing the owner's other installed apps. That file was destroyed.
#
# So settle, then check focus again, and only capture if it is still Kam AI. The
# second check is the point: a transition that is still moving will have changed.
sleep 1
focus_again="$("$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r' || true)"
if ! echo "$focus_again" | grep -q "com.kamsiob.kamai"; then
  echo "Refusing to capture: focus moved while settling." >&2
  echo "  focused window is now: ${focus_again:-unknown}" >&2
  exit 1
fi

"$adb" exec-out screencap -p > "$out"

# Orientation is part of whether a capture is usable. A run once produced ten
# landscape captures because the phone had rotated on the desk, and every crop
# and every tap coordinate in these tools assumes portrait, so it looked like
# nonsense output rather than a rotated screen.
if command -v python3 >/dev/null 2>&1 && [ -s "$out" ]; then
  if ! python3 -c 'import sys;from PIL import Image;w,h=Image.open(sys.argv[1]).size;sys.exit(0 if h>w else 1)' "$out" 2>/dev/null; then
    echo "Refusing to keep a landscape capture: the phone is rotated." >&2
    echo "  lock it with: adb shell settings put system user_rotation 0" >&2
    rm -f "$out"
    exit 1
  fi
fi

# An empty capture is a failure worth catching here rather than three steps later.
if [ ! -s "$out" ]; then
  echo "Refusing to keep an empty capture: $out" >&2
  rm -f "$out"
  exit 1
fi
echo "captured $out"
