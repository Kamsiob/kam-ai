# Roadmap

What is planned, what is not, and why. The
[issue tracker](https://github.com/Kamsiob/kam-ai/issues) is the authoritative
record of state; this is the shape of it in one place.

Kept current with the work rather than written once. If this file and an issue
disagree, the issue is right and this file is stale.

## Now: v1.0.0, the first public release

**Inference performance.** Cold time to first token (#38), build flags and ARM
microkernels (#51), quantization format per tier (#55), titling polluting the
conversation cache (#71), and sharing the instruction-block cache across
conversations (#72).

**Remaining quality work.** Brainstorm's method choice for someone being too
cautious (#73), Discover packs carrying full articles rather than introductions
(#13), and screenshots recaptured from the release build (#113).

**Model behavior.** The model reproducing text it was given: its own
instructions, its worked examples, or the user's message handed straight back
(#119, #122). Much of this is now caught before a user sees it, and the cause
turned out to be split between the model tier and the chat template rather than
the prompt, after five rounds of prompt editing that could not have worked. See
DECISIONS.md, "Demonstration regurgitation" and "The first turn is not like the
others". Logic Partner still engages poorly with some arguments (#124).

**First run and downloads are done.** #75 and #121 closed: the checks that cost
the user something happen before an offer rather than after it is chosen, and the
background download survives Android's daily foreground service limit.

## Blocked

**The project board and CI activation** (#99, #110, #111). These need GitHub
token scopes and repository settings that require the repository owner. The board
is written and waiting as an idempotent script at `tools/setup_board.sh`; the CI
workflow is parked at `docs/ci/ci.yml` rather than `.github/workflows/`.

## Next: a Linux desktop version

Planned, not started, and not begun until the Android release ships. Two things
are decided first, because each changes what the work is: its relationship to the
existing desktop tool in this family that already manages local model serving,
and its scope.

Linux only. macOS and Windows are out of scope, because what cannot be verified
cannot be supported, and an unverified platform is worse than one that does not
exist.

The parts that must stay identical across both platforms are tracked rather than
assumed: the database schema, the export and import format including its version,
the design tokens, the mode definitions and their system instructions, and any
user-facing copy appearing in both. Those are precisely where two independently
built platforms diverge silently.

## Deliberately not planned

These are decisions with reasoning recorded in
[DECISIONS.md](DECISIONS.md), not gaps.

- **Sync between devices.** The data model is sync-ready and no sync exists. That
  is a deliberate pair: the schema carries what a future sync would need so it
  never has to be retrofitted, and building sync would mean either a server or a
  key exchange, both of which contradict the point of the app.
- **An account.** There is nothing to sign into and nothing to sign into it with.
- **Analytics, crash reporting, or any telemetry.** No such library is in the
  build, and the absence is checked rather than asserted.
- **A companion persona.** The app is a thinking and drafting tool and says so.
  Modes withhold what a person expects an assistant to hand over, on purpose.
- **Model settings exposed to the user.** Temperature and the rest are fixed per
  mode. A control that makes answers worse is not a feature.
