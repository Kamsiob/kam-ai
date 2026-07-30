#!/usr/bin/env bash
# Seeds a chat list that reads like ordinary use, for the screenshots (#113).
#
#   tools/seed_chats.sh
#
# ## Why this is a script and not a session's worth of taps
#
# Seeding has failed three times in this project and every failure was quiet. Two
# runs did nothing at all and reported success. A third put six messages into one
# conversation, because the mode chip was tapped when the app was not on the chat
# list, so every message landed in whatever chat happened to be open.
#
# So the shape here is: **start each conversation from a known screen, and stop dead
# the moment one does not complete** rather than carrying on and leaving a list that
# is wrong in a way nobody can see.
#
# **What this cannot check, and you must:** it has no way to count the conversations
# afterwards. say.sh waits for the engine to report a finished generation, and a
# finished generation is not the same thing as a conversation existing. That is not
# hypothetical: the Workbench chip below used to be in this list, and it reported
# eight of nine seeded when the eighth had produced no conversation at all. Read the
# list on the device and count it.
#
# ## Workbench is not one of these, and that is not a bug
#
# Workbench is its own screen with its own input, persisted as the `workbench.input`
# setting rather than as a conversation. Tapping its chip opens that screen, and its
# composer sits close enough to a chat's that say.sh will happily type into it and
# wait out a real generation. Nothing lands in the chat list, and nothing says so.
#
# So the modes seeded here are the three that make conversations. The Workbench frame
# in the screenshot set comes from the screen itself, which is what it should show.
#
# ## What goes in it, and what must not
#
# Ordinary subjects, ordinary phrasing, a spread of modes. Nothing about this
# development process, a defect, a privacy test, or a hostility probe. Every message
# below is something a person might plausibly type on a Tuesday, and the replies are
# whatever the model says, which is the point: a seeded list has to be real output.
#
# **Ages are not set here.** Everything seeded lands within a few minutes, and nine
# conversations all stamped "now" reads as a device that was set up this morning. The
# ages are spread afterwards through the backup round trip, which is the only route
# to the timestamps that does not involve reaching into the database: export, rewrite
# createdAt and updatedAt, re-encrypt, import with Replace. See HANDOFF, #113.
set -euo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/lib/loud.sh"
adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

# The mode chips along the bottom of the chat list. One tap on a chip starts a new
# conversation in that mode, which is the app's only "new chat" action.
declare -A CHIP_X=([General]=160 [Logic]=422 [Brainstorm]=667)
CHIP_Y=2122

# mode|message. One message each: a seeded list wants plausible openings, not
# transcripts, and every extra turn is another generation to wait out.
SEEDS=(
  "General|What is the best way to get a tomato sauce stain out of a white shirt?"
  "General|How long do dried chickpeas need to soak before I cook them?"
  "General|The tips of my houseplant leaves are going yellow. What causes that?"
  "Logic|If I put aside 200 a month at 4 percent, roughly what would I have after five years?"
  "Logic|Is it cheaper to buy a 900 laptop that lasts three years or a 1500 one that lasts five?"
  "Brainstorm|Ideas for a fortieth birthday that is not a big night out."
  "Brainstorm|Ways to make the morning routine less rushed with two kids."
)

# Back out to the chat list from wherever we are, and refuse to continue if that
# did not work. This is the check the six-messages-in-one-chat run did not have.
to_chat_list() {
  for _ in 1 2 3; do
    "$adb" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 1
  done
  if ! "$adb" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q com.kamsiob.kamai; then
    "$adb" shell am start -W -n com.kamsiob.kamai/.MainActivity >/dev/null 2>&1 || true
    sleep 5
  fi
  "$adb" shell input tap 143 2270   # the Chats tab
  sleep 2
}

echo "== seeding ${#SEEDS[@]} conversations =="
i=0
for seed in "${SEEDS[@]}"; do
  i=$((i + 1))
  mode="${seed%%|*}"
  text="${seed#*|}"
  x="${CHIP_X[$mode]:-}"
  [ -n "$x" ] || { echo "Unknown mode '$mode'." >&2; exit 1; }

  to_chat_list
  "$adb" shell input tap "$x" "$CHIP_Y"     # starts a new chat in that mode
  sleep 6

  # 240s, not the default. The first generation after the app starts loads five
  # gigabytes of weights, and Brainstorm answers at length.
  if ! ./tools/say.sh "$text" 240 >/dev/null; then
    echo "Aborting at $i ($mode): the reply never completed, so this conversation" >&2
    echo "  is half seeded and the list would be wrong. Nothing after it was sent." >&2
    exit 1
  fi
  printf '  %d/%d  %-10s %s\n' "$i" "${#SEEDS[@]}" "$mode" "${text:0:56}"
done

to_chat_list
echo ""
echo "Seeded ${#SEEDS[@]} conversations. Verify each one on the device rather than"
echo "trusting this line: read the list, and read every title and snippet as a"
echo "stranger would. Then spread the ages through the backup round trip."
