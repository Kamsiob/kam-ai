#!/usr/bin/env bash
# Fails when a script in tools/ can report success it did not earn.
#
# The harness has produced a confident wrong conclusion five times: fixed timer
# waits, composer text persisting, the keyboard eating Back, a counter going
# negative, and say.sh printing a failure while exiting zero. Each was found by
# somebody tripping over it, which is the expensive way, and the fifth defeated a
# caller that had been written correctly.
#
# So the class gets a checker rather than another individual fix. What it looks for
# is deliberately narrow: patterns that are mechanically decidable and that have
# actually caused a wrong conclusion here. It is not a style linter, and it must stay
# quiet enough to be believed.
#
#   tools/check_scripts.sh
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0
note() { echo "$1" >&2; fail=1; }

scripts=$(find tools -name '*.sh' -not -path 'tools/lib/*' -not -name 'check_scripts.sh')

# 1. Every script aborts on error. Checked anywhere in the file, not in the first N
#    lines: four scripts here carry long comment headers and set their flags below
#    them, and a check that only read the top reported them all as defects.
for f in $scripts; do
  grep -qE '^set -euo pipefail' "$f" || note "NO set -euo pipefail: $f"
done

# 2. Nothing prints a failure as its last act. This is the say.sh defect exactly: the
#    warning was the final command, `echo` succeeded, and the script exited 0 while
#    telling the caller nothing had arrived.
for f in $scripts; do
  last=$(grep -vE '^\s*(#|$)' "$f" | tail -1)
  case "$last" in
    *'>&2'*) note "WARNS THEN EXITS ZERO (last line is a stderr message): $f" ;;
  esac
done

# 3. A count that becomes evidence is never allowed to be empty.
#
#    `n=$(grep -c ... || true)` looks harmless because grep -c exits 1 on no matches
#    and prints 0. But when the file is missing or adb died, grep prints nothing, the
#    variable is empty, and every arithmetic use reads it as zero. A rate computed
#    from it says "the guard never fired" when it means "nothing was measured".
#    Use count_matching from tools/lib/loud.sh instead.
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  note "COUNT CAN BE EMPTY (use count_matching from lib/loud.sh): $hit"
done < <(grep -nE '="?\$\(.*grep -c.*\|\|[[:space:]]*(true|:)' $scripts || true)

# 4. Evidence capture is never suppressed. shot.sh returns non-zero when Kam AI is
#    not genuinely on screen, and that refusal is the thing keeping somebody's
#    notifications out of this repository. Suppressed, a walk reports every step
#    completed with captures missing, and the crash-free walk is a release gate.
#    Use capture_or_note plus evidence_exit.
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  note "SUPPRESSED CAPTURE (use capture_or_note from lib/loud.sh): $hit"
done < <(grep -nE 'shot\.sh.*\|\|[[:space:]]*(true|:)' $scripts || true)

# 5. A send whose failure is suppressed. say.sh now exits non-zero when no reply
#    completed, and suppressing that puts back the defect it was fixed for. A caller
#    that genuinely must continue has to record the miss and fail at the end, the way
#    the walks now do.
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  note "SUPPRESSED SEND (record the miss and fail the run instead): $hit"
done < <(grep -nE 'say\.sh.*\|\|[[:space:]]*(true|:)' $scripts || true)

# 6. Recording a miss without failing on it is the same defect one level up: the
#    run knows evidence is absent and still exits zero. capture_or_note only means
#    something if evidence_exit is reached.
for f in $scripts; do
  if grep -q 'capture_or_note\|EVIDENCE_MISSES=\$((' "$f"; then
    # Anchored so a mention in a comment does not count as a call. The first
    # version of this rule matched the word in prose and passed a script that
    # recorded misses and never acted on them, which is the defect it exists to
    # find, one level up.
    grep -qE '^[[:space:]]*evidence_exit[[:space:]]*$' "$f" ||
      note "RECORDS MISSES BUT NEVER FAILS (add an evidence_exit call): $f"
  fi
done

if [ "$fail" -ne 0 ]; then
  echo "" >&2
  echo "tools/check_scripts.sh found scripts that can report a pass they did not" >&2
  echo "earn. Each pattern above has produced a wrong conclusion in this project." >&2
  exit 1
fi
echo "tools/: no silent-failure patterns found."
