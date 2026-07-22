#!/usr/bin/env bash
# Captures a screenshot only when Kam AI is the foreground app.
#
# Screenshots of a phone are screenshots of somebody's life. This refuses to
# capture anything unless Kam AI is actually on screen, so a mistimed tap or a
# stray back gesture can never pull a notification, a calendar, or an inbox into
# the repository.
set -euo pipefail
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
out="${1:?usage: shot.sh <output.png>}"

top="$("$adb" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity || true)"
if ! echo "$top" | grep -q "com.kamsiob.kamai"; then
  echo "Refusing to capture: Kam AI is not in the foreground." >&2
  echo "  foreground was: $(echo "$top" | grep -oE '[a-z0-9_.]+/[A-Za-z0-9_.]+' | head -1)" >&2
  exit 1
fi
"$adb" exec-out screencap -p > "$out"
echo "captured $out"
