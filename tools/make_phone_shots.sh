#!/usr/bin/env bash
# Derives the eight Play listing screenshots from the canonical capture set.
#
#   tools/make_phone_shots.sh
#
# The listing set used to be five files committed by hand, inside a commit about
# something else, with no script that produced them and no record of which build
# they came from. That is how a superseded asset survives a replacement: nothing
# regenerates it, so nothing notices it is old. One of the five was still the mode
# picker after that screen stopped being the way a mode is chosen.
#
# So the listing set is now derived rather than curated. Every file here is a crop
# of a frame in docs/screenshots, which is captured by tools/capture_store.sh from
# the `releaseCheck` build. Replacing a listing image means recapturing the frame
# and running this again. There is no step where a picture is chosen by hand.
#
# The crop removes two things and nothing else:
#
#   the status bar, top 110 rows      the clock, the battery and the signal
#                                     belong to whoever held the phone, not to
#                                     the application
#   the gesture pill, bottom 70 rows  drawn by the system, not by the app
#
# Both cuts land inside a uniform band, measured rather than guessed: status bar
# content ends by row 100 and rows 100 to 180 are a single colour, while the pill
# sits at about row 2364 with uniform rows either side of it. The application's own
# bottom tab bar is kept, because that is the app's user interface and a listing
# should show it.
#
# 1080 by 2224 clears the Play minimum of 1080 on the short side and the 16:9 to
# 9:16 ratio bound.
set -euo pipefail
cd "$(dirname "$0")/.."

src="docs/screenshots"
out="store-assets/phone"
crop="1080x2224+0+110"

# Eight frames, which is what Play allows, in the order they appear on the listing.
#
# The shape of the run: what it is, how it is organized, then one frame per mode
# because the modes are the whole idea, then the two things that keep somebody
# coming back, and finally the promise about their data. A browser who stops after
# three has still seen the app and its central claim.
#
# **Two screens were deliberately left out, and the reasons are worth keeping.**
#
# `model-light` is a good screen and an honest one: it measures speed on the phone
# it is running on and says plainly where a smaller model is weaker. It is cut
# because it answers a question nobody has before installing, and `08-on-device`
# already carries the on-device story to a browser.
#
# `followups-light` is cut because saved items are a thing you discover by using
# the app, and a screenshot of a list of two saved answers does not read as a
# reason to install.
#
# Every one of these is a crop of a real capture. Nothing here has a caption
# painted on it, no device frame, and no marketing text, because the moment a
# screenshot carries a claim the image is making a promise the app has to keep.
frames=(
  "01-a-conversation:conversation-light"
  "02-chats-and-modes:chats-light"
  "03-logic-partner:logic-light"
  "04-brainstorm:brainstorm-light"
  "05-workbench:workbench-light"
  "06-projects:projects-light"
  "07-discover:discover-light"
  "08-on-device:settings-light"
)

command -v magick >/dev/null || { echo "ImageMagick is needed." >&2; exit 1; }

# Removed first, so a renamed or dropped frame cannot leave its old file behind.
# This is the whole point of the script.
rm -f "$out"/*.png
mkdir -p "$out"

for pair in "${frames[@]}"; do
  name="${pair%%:*}"
  frame="${pair##*:}"
  if [ ! -f "$src/$frame.png" ]; then
    echo "Missing $src/$frame.png. Run tools/capture_store.sh first." >&2
    exit 1
  fi
  magick "$src/$frame.png" -crop "$crop" +repage "$out/$name.png"
  printf '  %-22s from %-20s %s\n' "$name.png" "$frame.png" \
    "$(magick identify -format '%wx%h' "$out/$name.png")"
done

echo
# Counted, not typed. This line said "five" while the array held eight, which is
# a small version of the exact drift this script exists to prevent.
echo "${#frames[@]} frames in $out, each traceable to a canonical capture"
echo "Play allows 8. Check every one after regenerating: the crop moves both edges,"
echo "so a row that read fine in the full frame can be half cut in the listing one."
