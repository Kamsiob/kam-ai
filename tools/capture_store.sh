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
#
# ## Two things this got wrong, both fixed
#
# **A refused capture used to be survivable.** `grab` printed "REFUSED" and returned
# success, so the run finished, said "captured into docs/screenshots", and exited 0
# with frames missing. Worse than missing: the *previous* run's file was still
# sitting there, so the set looked complete and was silently stale. That is the same
# defect make_phone_shots.sh was written to close on the listing side, and it was
# still open on this side. The directory is emptied first now, and a refusal fails
# the run.
#
# **The set was eight frames and the canonical set is ten.** Projects and Follow-ups
# were in docs/screenshots and were not produced by this script, so nothing
# regenerated them and nothing would have noticed them going stale. They are captured
# here now, which is the whole premise of having a script.
set -euo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/lib/loud.sh"
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
out=docs/screenshots

# Emptied rather than overwritten, so a refused capture cannot leave the previous
# run's file behind looking current.
rm -f "$out"/*.png
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

# Records a refusal and keeps going, and [evidence_exit] fails the run at the end, so
# a set with holes in it can never be read as a complete one.
grab() { capture_or_note tools "$out/$1-$THEME.png" && echo "    $1-$THEME"; }

# Row centres, measured rather than guessed. The old values were 560, 760 and 960;
# 960 falls in the gap below the third card rather than on it, which taps nothing.
ROW1=574; ROW2=730; ROW3=886

# **Which conversation sits in which row decides what three of these frames show.**
# Rows one, two and three become the conversation, logic and brainstorm frames, so
# the seeded ages are set to put a General, a Logic and a Brainstorm chat in that
# order. See HANDOFF, #113. If the list is reseeded, check that before capturing.
capture_all() {
  home;                                    grab chats
  "$adb" shell input tap 540 "$ROW1"; sleep 4; grab conversation
  home
  "$adb" shell input tap 540 "$ROW2"; sleep 4; grab logic
  home
  "$adb" shell input tap 540 "$ROW3"; sleep 4; grab brainstorm
  home
  "$adb" shell input tap 924 2122; sleep 6; grab workbench
  home
  "$adb" shell input tap 406 2252; sleep 5; grab projects
  home
  "$adb" shell input tap 671 2252; sleep 5; grab followups
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
echo ""
echo "Twenty frames is the whole set. Now read every one of them as a stranger"
echo "would, including anything half visible at an edge, because a partially"
echo "scrolled row still reads. The guard stops the wrong application being"
echo "photographed; it does not stop the right one showing something it should not."

# Fails the run if any capture was refused. The steps ran; without the evidence
# that they ran as described, this is not a complete set.
evidence_exit
