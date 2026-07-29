#!/usr/bin/env bash
# Derives the five Play listing screenshots from the canonical capture set.
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

# Five frames, in the order they appear on the listing. The first is what the
# application is, the last is the promise it makes about your data, and the three
# between are the reasons to keep it.
#
# `choosing-a-model` replaces the mode picker that used to hold this slot. A mode
# is now chosen from the chat list, which frame 02 already shows, and the model
# screen earns a slot on its own: it measures speed on your own phone and says
# plainly where a smaller model is weaker.
frames=(
  "01-a-conversation:conversation-light"
  "02-chats-and-modes:chats-light"
  "03-choosing-a-model:model-light"
  "04-discover:discover-light"
  "05-on-device:settings-light"
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
echo "five frames in $out, each traceable to a canonical capture"
