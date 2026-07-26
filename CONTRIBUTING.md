# Contributing

Maintained by one person, so response times vary. That is the honest starting
point rather than a disclaimer at the bottom.

## Reporting something

Open an issue using one of the templates. The templates ask for acceptance
criteria, which is the part that matters: without them, closing an issue is a
judgement call and nobody can verify the claim afterwards.

For a bug, the device and Android version are required, because nothing here is
considered fixed until it has been seen working on real hardware.

Security problems go through the private route in [SECURITY.md](SECURITY.md), not
a public issue.

## Proposing a change

Open an issue before writing code, unless the change is trivial. Not
bureaucracy: the specification documents in this repository are authoritative, and
a change that contradicts them needs the document changed too, which is a
conversation rather than a diff.

Implementation here is written by a coding agent working from those specifications.
That is why they are treated as authoritative and why they are expected to stay
current with the code rather than being written once. If you change behaviour,
change the document that describes it in the same pull request.

## Setting up

- JDK 21, Android SDK with NDK, CMake. Versions are pinned in
  `gradle.properties` and `gradle/libs.versions.toml`.
- `./gradlew assembleDebug` builds it. The native build compiles llama.cpp and
  whisper.cpp from source, so the first build is slow.
- `./gradlew testDebugUnitTest` runs the unit suite.
- `./gradlew compileDebugAndroidTestKotlin` compiles the instrumented tests.
  **Run this.** They are not part of the default build, which is how they were
  once allowed to rot unnoticed; continuous integration now compiles them on
  every push and so should you.

Instrumented tests run with `adb shell am instrument`, never
`connectedAndroidTest`, which uninstalls and reinstalls the app and therefore
destroys whatever is on the device.

## Conventions

**Commits.** One shape, followed without exception:

```
<kind>: <short imperative summary> (#123)

Body, where the reasoning is not obvious from the diff. What was tried,
what was found, why this way and not the other way.
```

`<kind>` is one of `feat`, `fix`, `perf`, `docs`, `refactor`, `test`, `build`,
or `chore`. Subject line under about seventy characters, present tense, and the
issue number referenced so every commit traces to a reason and every issue
traces to the code that resolved it.

The particular convention matters far less than sticking to it. A log where
every message has the same shape reads as one project; a log mixing three styles
reads as whoever happened to be typing that day. This repository mixed area
prefixes with kind prefixes until it was settled here, and history is not
rewritten to match, so commits before that point look different and are left
alone. Dependabot's own `Bump ...` subjects are also left as they are.

**Signing.** Commits are signed with an SSH key. If you are contributing, sign
yours too, or say so in the pull request if you cannot.

**Branches.** Name them for the issue they address. One logical change per
branch. Anything that changes behaviour goes through a pull request; typos and
documentation touch-ups may go straight to `main`.

**Comments.** Explain the reasoning, not the mechanics. A comment saying what a
line does is noise; one saying why it is that way, or what went wrong the first
time, is the reason the file is readable a year later. Several files here carry a
note about an approach that failed and must not be retried, and those are the most
valuable comments in the codebase.

**Copy.** No em dashes in anything a person reads: interface text, notices,
onboarding, help, error messages, and the documents in this repository. The rule
does not extend to code, identifiers, regular expressions or test fixtures, and
nothing functional should be bent to satisfy it. `EmDashScopeTest` enforces both
halves.

American spellings in user-facing text. `PublicCopyTest` enforces it.

Gold is reserved for saved items, locked model tiers, the support action, and
destructive labels. `GoldRuleTest` fails if it appears anywhere new, in either
direction.

## Testing expectations

- A behaviour change comes with a test that would have failed before it.
- Prefer pure functions that can be tested without a device. Most of the
  interesting logic here (scroll following, memory supersession, download guards,
  prompt budgets) is pure precisely so it can be.
- Anything a user can see is verified on a device before the issue is closed.
- Test names are sentences describing the case, and the test body says why the
  case matters. A test called `testScroll` tells the next person nothing.

## What will and will not be accepted

Welcome: bug fixes with a reproduction, accessibility improvements, a translation
once there is a string extraction pass to hang one on, and anything that makes an
honest limitation clearer.

Unlikely: cloud sync, accounts, or anything that sends conversation content off
the device. That is a deliberate product position, not a gap, and it is recorded
in DECISIONS.md.

Also unlikely: a dependency that could be a hundred lines of local code, a
persona or companion framing for the assistant, or a change that makes the app
claim more than it can do.

If you are unsure, open an issue and ask. A rejected idea discussed early costs
far less than a rejected pull request.
