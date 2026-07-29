#!/usr/bin/env bash
# Helpers for scripts that produce evidence, so a failed measurement cannot look
# like a measurement of zero.
#
# Source it: . "$(dirname "$0")/lib/loud.sh"
#
# ## Why this exists
#
# Five defects in this project's harness have each produced a confident wrong
# conclusion: fixed timer waits, composer text persisting between messages, the
# keyboard eating Back, a counter going negative, and say.sh printing a failure
# while exiting zero. The last one is the worst of the five, because the caller was
# written correctly: mode_battery.sh deliberately has no `|| true` on that call, and
# the exit code defeated that care.
#
# They share one shape. **A measurement that fails silently reads as a measurement
# of zero, and zero is a publishable number.** The thermal question is the clearest
# case: 71 samples all reading status 0 against an idle phone was reported as
# evidence that LIGHT is rare, when it was evidence that nothing was running.
#
# ## The specific trap these guard
#
#   rejected=$(grep -c 'KamEcho.*rejected' "$log" || true)
#
# `grep -c` exits 1 when it matches nothing, which is why the `|| true` is there and
# it looks harmless: it prints "0" and the count is genuinely zero. But if the log is
# missing, or adb failed, or the logcat buffer aged out, grep prints **nothing** and
# the variable is the empty string. Every arithmetic use of it then treats it as
# zero, and a rate computed from it reads as "the guard never fired" rather than "the
# measurement did not happen".
#
# The rule these encode: a count is either a number or a failure, never an absence
# that looks like a number.

# A non-negative integer, or the script dies saying which measurement failed.
#
# Use for anything whose value becomes evidence. Do not use for a best-effort
# environment read, where an empty answer is a legitimate "not set".
require_count() {
  local label="$1" value="$2"
  if [ -z "$value" ]; then
    echo "FAILED MEASUREMENT: $label produced no value at all." >&2
    echo "  An empty count is not zero. Something did not run: a missing log, a" >&2
    echo "  dead adb, or a logcat buffer that aged out." >&2
    exit 1
  fi
  case "$value" in
    ''|*[!0-9]*)
      echo "FAILED MEASUREMENT: $label produced '$value', which is not a count." >&2
      exit 1
      ;;
  esac
  printf '%s' "$value"
}

# Counts matching lines and never lies about it.
#
# `grep -c` exiting 1 on no matches is expected and fine; grep exiting 2 is a real
# error (unreadable file, bad pattern) and is not survivable for a measurement.
count_matching() {
  local label="$1" pattern="$2" file="$3"
  local n status
  n="$(grep -c -- "$pattern" "$file" 2>/dev/null)" && status=0 || status=$?
  if [ "$status" -ge 2 ]; then
    echo "FAILED MEASUREMENT: $label could not read '$file' (grep exit $status)." >&2
    exit 1
  fi
  # A clean "no matches" is a real zero. Anything else empty is not.
  [ -n "$n" ] || n=0
  require_count "$label" "$n"
}

# Counts matches in a command's output, distinguishing "it found nothing" from "it
# did not run".
#
#   crashes=$(adb_count "crash lines" "com.kamsiob.kamai" "$adb" logcat -d -b crash)
#
# **This is the worse half of the count trap and it is not the empty-string one.**
# In `n="$("$adb" logcat -d | grep -c pattern || true)"`, if adb is missing or the
# device has gone away, adb writes nothing, `grep -c` prints "0" and exits 1, and the
# `|| true` turns that into a clean-looking zero. The variable is never empty. So the
# run reports zero crashes, zero rejections, zero of whatever it was counting, and
# every one of those is a publishable number that means "the measurement did not
# happen".
#
# That is the thermal defect exactly: 71 samples all reading status 0 against an idle
# phone, reported as evidence LIGHT is rare. The zeroes were real readings of nothing.
#
# So the upstream command's exit status is checked on its own, before anything counts
# its output.
adb_count() {
  local label="$1" pattern="$2"
  shift 2
  local raw n
  if ! raw="$("$@" 2>/dev/null)"; then
    echo "FAILED MEASUREMENT: $label — the command producing the output failed." >&2
    echo "  Command: $*" >&2
    echo "  A zero here would have been indistinguishable from a real count of zero." >&2
    exit 1
  fi
  # grep -c exits 1 on no matches, which is a genuine zero and is fine. Only the
  # upstream failing is fatal, and that was checked above.
  n="$(printf '%s\n' "$raw" | grep -c -- "$pattern")" || n=0
  require_count "$label" "$n"
}

# Evidence capture that refuses to be skipped quietly.
#
# shot.sh returns non-zero when Kam AI is not genuinely on screen, and that refusal
# is correct: it is what stops a stray notification shade reaching the repository. It
# happened during this session, when a mistimed tap at a large font size opened a
# browser.
#
# The walks suppressed that with `|| true`, so a run could report 33 steps completed
# with evidence missing from several of them, and the crash-free walk is a release
# gate. This records the miss and keeps going, then [evidence_exit] fails the run, so
# a walk with holes in it cannot be read as a clean one.
EVIDENCE_MISSES=0
capture_or_note() {
  local script_dir="$1" path="$2"
  if ! "$script_dir/shot.sh" "$path" >/dev/null 2>&1; then
    EVIDENCE_MISSES=$((EVIDENCE_MISSES + 1))
    echo "  MISSING EVIDENCE: no capture for $path" >&2
  fi
}

# Call at the end of any run that used [capture_or_note].
evidence_exit() {
  if [ "$EVIDENCE_MISSES" -gt 0 ]; then
    echo "" >&2
    echo "This run is INCOMPLETE: $EVIDENCE_MISSES capture(s) failed." >&2
    echo "  Do not report it as a pass. The steps ran; the evidence that they" >&2
    echo "  ran as described does not exist for all of them." >&2
    exit 1
  fi
}
