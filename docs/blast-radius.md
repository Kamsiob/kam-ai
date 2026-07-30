# Blast radius: which results rest on which instrument

Every instrument found broken this session, and everything it produced.

**Why this is a document and not a series of judgements.** Case by case is how the crash walk
survived: each figure looked reasonable on its own, and nobody asked what had produced it. So
this is organized by *instrument* rather than by result, and the question asked of each is the
same one: if this tool were broken in the way it was broken, what would it have reported?

**The first finding is about the record itself.** DECISIONS.md almost never names the tool that
produced a figure. It says what was measured and what the number was. `mode_battery` appears
twice in nine and a half thousand lines; `clean_statements`, `input_styles`, `memory_leak` and
`thermal_frequency` appear zero times. So the record cannot be audited by looking up a tool
name, and this table had to be reconstructed from what each tool is for.

**That is a defect in the record, not just an inconvenience.** Going forward, a recorded
measurement names the script that produced it, so the next blast radius audit is a grep instead
of an archaeology exercise.

---

## The instruments, their defects, and what they touched

### `crash_walk.sh` and `crash_walk2.sh` — **results void, since re-earned**

Two defects. `grep -c` with a suppressor inside a pipeline printed a clean zero when the device
was gone, so the walk could not tell "nothing crashed" from "nothing ran". And the crash count
was cumulative compared against zero, so one crash reported as many.

| result | status |
|---|---|
| the 33 step crash-free pass, the release gate | **was void, re-earned** on the fixed instrument: 22 + 11 steps |
| "7 crashed" in the first repaired run | **wrong number, right verdict.** One crash, #146 |

The repaired instrument's first act was to find #146, a release blocking crash the broken one
had passed. That is the strongest single argument in this file.

### `say.sh` — **the widest radius, and the least visible**

It printed "no reply completed" and exited 0. Every caller was told nothing arrived and carried
on as though it had.

**What that means for every battery ever run:** a case whose reply had not finished within the
ceiling was captured anyway, and the capture shows either a half-written reply or the previous
screen. Nothing marked it. So in any battery result, an unknown subset of cases may be
measuring a reply that had not arrived.

| affected | status |
|---|---|
| every `mode_battery` run | **suspect.** Cases are individually readable from captures, so a re-read can recover them |
| every `tier_battery` run | **suspect**, same |
| `clean_statements`, `input_styles` | **suspect**, same |
| the batteries' *conclusions* about reply quality | **mostly survive**, because they were reached by reading replies, and a missing reply is visible when read |

The asymmetry is worth stating: **counts are void, readings are recoverable.** A tally cannot
tell you which entry was a timeout. A capture can be looked at again.

### The guard, via `ALWAYS_ALLOWED` — **every intervention count void**

Not a script, but an instrument all the same: the batteries counted the guard's rejections
rather than reading replies. `isAllowedOutright` exempted anything merely beginning with an
allowed sentence, and the model is instructed to produce those sentences.

So a reply could carry a complete recital, register zero interventions, and be counted clean.

| result | status |
|---|---|
| the sequence seven, three, nine, two, zero, two, zero | **void** |
| interventions across 30 cases, 7 / 2 / 0 | **void** |
| "zero guard rejections on that run" | **void** |
| "six interventions across three runs" on Logic Partner | **void** |
| "two interventions, both in logic values" | **void** |
| rejection counts per wording | **void** |
| the tier finding's E4B versus E2B tallies | **void as figures** |
| **the tier finding's conclusion** | **stands.** It rested on replies that were read and compared; the tallies were corroboration |

There is no way to tell from the numbers which zeros were real, which is what makes them void
rather than suspect.

### `echo_rate.sh` — **void**

Both counts came from `grep -c ... || true` against a log file. A missing file made the
variable empty, and a rate computed from an empty numerator reads as zero: "the guard never
fired" when it means "nothing was measured".

Any recorded echo rate is void. The tool is fixed; no figure from it has been re-earned yet.

### `prefix_probe.sh` — **conclusions stand, attribution was guesswork**

It read the whole logcat buffer once at the end, and logcat is circular. Six turns produced five
lines, one of which was the titler.

| result | status |
|---|---|
| ranked order costing 275 and 357 tokens, 10.1 and 11.5 seconds | **stands.** The effect was large and repeated, and the direction is not in doubt |
| newest-first giving 42 to 88 tokens, 2.5 to 5.1 seconds | **stands**, same reasoning |
| the floor's 444 token, 12.5 second spike | **stands**, and the per-turn read will confirm the attribution |
| which turn each line belonged to | **was inference**, now recorded per turn |

### `thermal_frequency.sh` and its `/tmp` predecessor — **void, already known**

71 samples all reading status 0 against an idle phone, reported as evidence that LIGHT is rare.
It measured an idle phone. #134 is open on exactly this and the figure was already treated as
void.

### `memory_leak.sh` — **suspect on evidence, conclusion probably stands**

It suppressed `shot.sh` failures, so a capture that was refused went unrecorded.

The eight-probe result that a planted memory was never recited **probably stands**, because it
was a negative result read from replies rather than a count. But if any of the eight captures
was silently missing, the sample was smaller than eight. Worth re-running now that captures
fail loudly, and cheap to do.

### `workbench_check.sh`, `input_styles.sh` — **no recorded results to void**

Both suppressed captures. Neither appears in DECISIONS.md, so nothing rests on them. Fixed
before they were used for anything.

---

## What has to be re-run, in priority order

1. **`echo_rate.sh`** — nothing has been re-earned and the guard changed underneath it.
2. **The battery, counting interventions *and* reading replies** — so the two can disagree, and
   the difference is the size of what the bypass hid. The reply check must not use the same
   partial-match shape as the three bypasses it exists to catch.
3. **`memory_leak.sh`** — cheap, and it restores a sample size.
4. **`prefix_probe.sh`** — to confirm the 444 token attribution now that lines are per turn.
5. **#134 under sustained inference load** — the only way to answer a question that has never
   been measured rather than mis-measured.

## What does not need re-running

The crash gate, which has been re-earned. The tier finding's conclusion, which never rested on
the void tallies. The #133 floor and #144 toggle, both verified per case on the device this
session with the fixed tools. #146, verified by reproducing the crash before fixing it.
