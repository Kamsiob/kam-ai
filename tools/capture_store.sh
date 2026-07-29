#!/usr/bin/env bash
# The canonical screenshot set, both themes, from the running application.
#
#   tools/capture_store.sh
#
# Captured from the releaseCheck build, which is initWith(release): the same R8
# minification, the same shrunk resources, the same keep rules. It differs from
# release only in its signature, and a signature does not change a pixel, so these
# depict the release build. The real release APK cannot be installed here without an
# uninstall, which would destroy the Keystore entry wrapping the database key and
# cost a five gigabyte model re-download.
#
# The set is fixed and the filenames are stable, so replacing one is mechanical and
# does not require editing the README (#113).
#
# Every frame goes through shot.sh, which refuses to capture unless Kam AI is
# genuinely in front, and every frame must still be looked at before use: the
# guard stops the wrong application being photographed, not the right application
# showing something personal.
set -euo pipefail
cd "$(dirname "$0")/.."
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
out=docs/screenshots
mkdir -p "$out"

prior_rotation="$("$adb" shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
restore() {
  [ "${prior_rotation:-1}" = "1" ] && \
    "$adb" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
  "$adb" shell cmd uimode night no >/dev/null 2>&1 || true
}
trap restore EXIT
"$adb" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$adb" shell settings put system user_rotation 0 >/dev/null 2>&1 || true

home() {
  "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 6
  fi
  "$adb" shell cmd statusbar collapse >/dev/null 2>&1 || true
  "$adb" shell input tap 142 2270; sleep 3
}

grab() { ./tools/shot.sh "$out/$1-$THEME.png" >/dev/null && echo "    $1-$THEME" || echo "    REFUSED $1-$THEME"; }

capture_all() {
  home;                                   grab chats
  "$adb" shell input tap 540 560; sleep 4; grab conversation
  home
  "$adb" shell input tap 540 760; sleep 4; grab logic
  home
  "$adb" shell input tap 540 960; sleep 4; grab brainstorm
  home
  "$adb" shell input tap 925 2122; sleep 6; grab workbench
  home
  "$adb" shell input tap 936 2270; sleep 6; grab discover
  home
  "$adb" shell input tap 1013 219; sleep 4; grab settings
  "$adb" shell input tap 540 780; sleep 5; grab model
}

for mode in no yes; do
  if [ "$mode" = "no" ]; then THEME=light; else THEME=dark; fi
  echo "  $THEME theme"
  "$adb" shell cmd uimode night "$mode" >/dev/null 2>&1 || true
  sleep 3
  "$adb" shell am force-stop com.kamsiob.kamai >/dev/null 2>&1 || true
  sleep 2
  "$adb" shell am start -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
  sleep 10
  capture_all
done

echo "captured into $out"
