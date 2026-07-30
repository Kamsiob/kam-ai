# HANDOFF.md · Kam AI by Kamsiob

The resumable state of this project, for a session with no memory of any other. Read this
file in full. Then search MASTER_SPEC.md, DESIGN.md, DECISIONS.md, and the GitHub issues
for the area you are about to work on, rather than reading them end to end.

**Part I is the current state and is authoritative.** Part II is the five open blockers in
detail. Part III is the patterns that must survive, and it is the part most expensive to
rediscover. Part IV is what has already been tried and ruled out; read it before attempting
anything in those areas. Part V is machine and device reference. Part VI is the dated
historical record, kept for reasoning and not for status. **Where anything in Part VI
disagrees with Part I, Part I is right.**

**Standing owner rules that never expire.** No em dashes in any user-facing copy,
documentation, commit message, or store text. American English spelling. Gold is reserved
for saved items, locked tiers, the Support this work button, and destructive labels,
nowhere else. Secrets never enter this public repository. Every commit updates the specs
and this file. GitHub issues carry real working notes and close only when device-verified.
**Exactly one copy of this app exists on the phone, always the current build; never install
a second, for any reason.** The phone is off limits beyond this app, and never screenshot
unless Kam AI is in the foreground. **Never screenshot except through `tools/shot.sh`, and
never type except through `tools/say.sh`,** with no exceptions and no raw `screencap`. Do
not upload a bundle, submit for review, promote a track, tag the repository, or create a
release. Do not change the default model. Do not delete the owner's data. Work unattended:
surface something mid run only for a credential the owner alone holds, a false privacy
claim, or a crash.

**Nothing on the deferred list is cancelled. It is sequenced.** The quality bar has not
moved. Work deferred to the review window is deferred because the review window is free:
the clock starts at submission, and changing the binary restarts the queue. A first
submission from a new organization account takes one to two weeks.

---

# PART I: NOW

## 1. Exactly where the work stopped

**Item: #113, and it is now the whole of the screenshot work rather than two frames.**

The owner has given **explicit permission to delete conversations for this, overriding the
standing rule about never deleting their data.** That permission is specific to clearing the
chat list for screenshots. Everything else about not deleting still stands.

### The sequence, in order, and where it stopped

1. **Export everything and verify the round trip.** NOT DONE. This is the gate: nothing gets
   deleted before an exported file has been opened and confirmed to hold real conversations.
   The codec half is well covered by `BackupRoundTripTest` and `BackupFieldCoverageTest`; what
   is missing is the device half, which is the document picker, the passphrase, and merge
   versus replace. See `docs/coverage-audit.md`.
2. **Delete the test conversations, the probe conversations, and the ~30 archived ones.** NOT
   DONE, and deliberately not started. Archived is not enough: archived items can still
   surface in a view, so they are to be deleted rather than left archived.
3. **Seed a chat list that reads like ordinary use.** NOT DONE. Ordinary subjects, ordinary
   phrasing, a spread of modes, plausible count and age. Nothing referencing this development
   process, a defect, a privacy test or a hostility probe. **Verify each conversation rather
   than the exit code:** two seeding runs previously did nothing silently and a third put six
   messages into one conversation.
4. **Capture the full set in both themes for both destinations.** NOT DONE. Theme is drivable
   with `adb shell cmd uimode night yes|no`, and the owner has confirmed that is fine.
   - The **listing** set is judged against the listing bar: truthful *and* looking like
     ordinary use.
   - The **README** set is judged the same way and apparently never was. It is currently the
     worse of the two and a technical visitor sees it first.
5. **Confirm the script derives both sets and clears the directory first**, then upload the
   listing set and commit the README set, and confirm no superseded image survives in either
   place.

**Why it stopped here rather than partway through.** Deleting thirty-plus conversations is
irreversible, the export gate was not yet passed, and the session's context was thin. A
half-done deletion is the one state worse than not starting, so it was not started. Nothing is
in an intermediate state.

### The probe conversations currently in the list, all to be deleted

Two "Is the rowing club open on Sunday", two "How do I get a coffee stain out", "What are you",
"Tracking small home repairs", plus the walk's mode replies and the Discover discussion
attempts.

### Two new documents that replace guesswork, read these before re-measuring anything

- **`docs/coverage-audit.md`** — every feature marked device exercised, unit only, or never
  touched. It has corrected itself three times (the backup round trip, two Projects items, and
  Follow-ups), which is the argument for having written it rather than asserting coverage from
  memory. **Never exercised and still open: the four Follow-up entry points, App lock, Auto
  archive, the widget/tile/share/selection entry points, Discover beyond the card, Voice,
  onboarding on a fresh install, Workbench, and Projects create/move/delete.**
- **`docs/blast-radius.md`** — every instrument found broken and everything it produced,
  organised by instrument because case by case is how the crash walk survived. **Read this
  before trusting any figure in DECISIONS.md.** Its own first finding: the record almost never
  names the tool behind a number, so a recorded measurement should now name its script.

**What must be re-run, in priority order, from blast-radius:** `echo_rate.sh` (nothing
re-earned), the battery counting interventions *and* reading replies, `memory_leak.sh` (cheap,
restores a sample size), `prefix_probe.sh` (confirm the 444 token attribution), and #134 under
sustained load.

**What must NOT be re-run**, so the audit does not become an excuse to redo verified work: the
crash gate (re-earned), the tier finding's conclusion (never rested on the void tallies), and
#133, #144, #146 (verified this session with fixed tools).

### One rule earned the hard way today

**Never install while a device probe is running.** A build is not desk work, because it reaches
the device. A probe was contaminated and discarded for this. Desk work in parallel is right;
`gradlew` and `adb` are not desk work.

### Blockers: four, down from six

#146 and #144 are closed and device verified. Remaining: **#142, #137, #134, #113.** The
milestone holds exactly those four.

### The coverage audit is the other large open piece

`docs/coverage-audit.md`, new, states for every feature whether it is device exercised, unit
only, or never touched. **Never touched:** Projects entirely, Follow-ups entirely, App lock,
Auto archive, the widget and tile and share target and selection hook, Discover beyond the
card, Voice, onboarding on a fresh install, search scope, and Workbench.

Two of those cannot currently be reached by the harness at all (#147): `say.sh` dismisses the
keyboard, which closes the Discover discussion sheet, and Workbench is not a chat mode. Every
battery, probe and walk drives the app through the composer, so coverage stops at the main chat
and has stopped there silently.

### Test count, from a forced rerun with the results directory deleted first

**662 tests across 86 classes, 0 failures, 0 skipped.** Any count in any document is a claim
until re-measured this way.

## 2. The two lists

Every open issue now carries exactly one of two labels, `release-blocker` or
`deferred: review window`, so the two lists are readable from the tracker and not only
from this file. **Every new issue states its release-blocking judgement at the moment it
is opened, defaulting to deferred when unsure, and saying why.**

### What blocks release

| # | What | State |
| --- | --- | --- |
| | Crash-free pass on a fresh install of the release build | **Done.** 33 steps across two walks, both pass, on `releaseCheck`. The crash it found is fixed. |
| | Data safety declaration matches the built application | **Done.** Re-derived from source in `docs/release-data-safety.md`. Exactly two network calls. |
| | Every permission has a user-facing justification | **Done.** All seven traced to a feature and a listing location in `docs/release-permissions.md`. |
| | Target API level 36 | **Done.** `targetSdk` 36, `compileSdk` 37. |
| | No false claim about privacy, data handling, or what the app does with what a user types | **Partly done.** Three false claims found and fixed. The sweep has not been run end to end. See Part II. |
| 113 | Screenshots matching the application | **Nearly done.** 20 documentation frames finished. No longer blocked on #133. Needs the probe conversations cleared, then two frames recaptured. |
| 133 | Memory retrieval injects irrelevant facts | **Nearly done.** Ordering, relevance floor and interface half all fixed and device verified. Only `memory_leak.sh` plus #142's channel test remain. |
| 144 | A setting for whether the memory note appears | **Done.** Off by default, device verified, and it unblocked #113. |
| 137 | Hostility repetition, and an insult trips the character rule | **Open, reopened once.** Fix verified on one phrasing, still fails on another. |
| 134 | Nothing said or done when the phone throttles | **Fix committed, decision open.** Whether LIGHT should speak needs a measurement. |
| 142 | The echo guard checks one of six sources of prompt text | **De-escalating.** Both privacy-relevant channels tested clean. Two correctness channels left. |

### What is deferred to the review window

| # | What |
| --- | --- |
| 122 | A statement carrying its own answer sometimes gets restated. Five prompt levers tried and failed. |
| 109 | Adopt the branch and pull request workflow. |
| 110 | Require a pull request and passing checks before merging. |
| 111 | Require signed commits once the key is registered. |
| 112 | Build provenance on release artifacts. |
| 138 | The self-referential answers class. |
| 139 | Audit every pair of places that must agree. |
| 140 | Does the leakage class appear on Qwen3, which has a real system role. |
| 141 | The models screen is cluttered. |
| 13 | Discover packs ship only article introductions. |
| | Apply the over-firing-condition lesson from #137 to every conditional instruction across all six prompts. |

---

## 3. What requires the repository owner, in order

One sitting, no context needed. Everything preparable is prepared.

1. **Fix the milestone.** The `v1.0.0 (Android)` milestone currently misrepresents the
   release: it does not reflect the blocking and deferred split. Reconcile it against the
   two labels above.
2. **Transcribe the Play Console items that have no API.** Category, privacy policy URL,
   the data safety form, the content rating questionnaire, the ads declaration, target
   audience. All written out ready to copy in `docs/play-console-checklist.md`, in the
   order the Console asks, with the reasoning for each answer so it can be defended if
   queried. Confirmed impossible through the API by introspection: `AppDetails` carries
   only contact fields, `Listing` only text, and there is no data safety, privacy or
   category resource at all.
3. **Upload five phone screenshots**, once the two blocked frames are recaptured. They will
   be in `store-assets/phone`, 1080x2224, generated by `tools/make_phone_shots.sh`.
   Nothing has been uploaded to the Console yet, so no superseded asset can be lingering
   there; the lingering risk was in the repository and is now closed by the script, which
   deletes the directory before regenerating it.
4. **Install the real signed release build on the phone once, deliberately.** This is the
   last thing before submission and it has a real cost, so it is a decision rather than a
   step. It requires an uninstall, because the signature differs from every build installed
   so far. The uninstall destroys the Keystore entry that wraps the database key, making
   every existing conversation permanently unreadable, and it removes a five gigabyte model
   that must be downloaded again. **Do a verified export first**, meaning open the export
   file and confirm it contains real conversations, not merely that the export reported
   success.
5. **Register the commit signing key.** SSH signing works locally and every recent commit
   is signed, but the public key is not on the account, so they display as Unverified. One
   command, in DECISIONS.md under "Commit signing". GitHub evaluates signatures at display
   time, so every commit already signed becomes Verified the moment the key lands.
6. **Back up the keystore.** `~/.kamsiob-secrets/` holds `kam-ai-upload.jks` and its
   properties file, outside the repository by design, and it cannot be regenerated. Play
   App Signing means a lost upload key can be reset, which is the safety net, but the
   backup costs nothing.
7. **The copyright question**, recorded in DECISIONS.md under BLOCKED and left unresolved
   deliberately: the copyright status of machine written code and how it interacts with
   AGPL-3.0. No legal language was added and the license was not touched.

---

## 4. Queued work, from the most recent instructions

1. **Check every frame for content coherence, not only for debris and personal data.**
   Done for the 20 documentation frames. **The bar turned out to be two bars**, which is a
   finding worth keeping: a documentation frame has to be truthful, and a listing frame has
   to be truthful *and* look like ordinary use. `chats-light` passed the first and fails
   the second. See Part II, #113.
2. **Sweep every script for swallowed failures and make each fail loudly.** Four scripts
   fixed (`clean_statements.sh`, `prefix_probe.sh`, `tier_battery.sh`, `memory_leak.sh`)
   and committed as a class in `964c413`. **Not finished:** the sweep covered sends and
   evidence captures. Still to do is every remaining `|| true`, every ignored exit code,
   every missing value defaulted to zero, and every path that continues past an error
   without saying so, across all of `tools/`.
3. **Measure how often LIGHT occurs before deciding whether it should speak.** Instrument
   written (`tools/thermal_frequency.sh`), question unanswered. The first attempt sampled
   an idle phone and produced nothing usable. See Part III.
4. **Run the claims sweep end to end, including the model's own answers.** Still the
   largest remaining piece of release-blocking work. **Started, not finished.**

   **A fourth false claim was found and fixed, and it was the worst placed of the four.**
   `HARD_RULES` in `SystemPrompts.kt` *instructed* the model to answer "Everything works
   the same offline", and `PromptEcho.ALWAYS_ALLOWED` exempted that sentence from every
   guard on the written grounds that it was "true wherever it lands". It is not: two
   network calls exist, a download the user starts and the Discover pack manifest fetched
   when Discover opens. So "can I get new packs with no signal" or "do I need a connection
   to download a model" got a false answer from the one place in the app where nothing
   would catch it. Same defect as the worst of the original three: a sentence whose breadth
   implied there were no network calls at all. Now "Everything you type is handled the same
   with no connection", matching PRIVACY.md rather than overshooting it.

   Also fixed: onboarding described Discover as "Short reads from Wikipedia, offline",
   where the commas made "offline" a property of the feature rather than of the packs.

   **Newly checked:** every string in `app/src/main` matching network, offline, upload,
   tracking and data-location claim shapes, which covers the onboarding copy, the settings
   subtitles, the Discover explanatory copy and the help text. Assessed and found sound:
   `SupportSignpost` ("no ads, no tracking"), `VoiceScreen` ("no network and no account"),
   `BackupScreen`, `DiscoverSheets`, `QuestionsAndAnswers`, and slide 5's "No locked
   features" (which is about payment; the "locked" model tiers are a memory constraint).

   **Still not checked, and this is now the whole of the remaining sweep: the model's own
   answers.** Asked in every mode, on both tiers, about privacy, offline behavior and where
   data goes. Still the check most likely to be skipped, because it is not a string and no
   grep will find it, and the one most likely to produce a false claim, because the model
   generates the sentence fresh every time. The exemplar it is now told to follow is
   correct, which was not true before this session.

---

# PART II: THE OPEN BLOCKERS, PRECISELY

## #113, screenshots

**Done.** Twenty frames, ten screens in light and dark, captured from `releaseCheck`, in
`docs/screenshots`. The README points at them and every reference resolves to a file that
exists. Every superseded asset was removed rather than left in place: the old `chat-*`
pair, four singles with no theme suffix, the `modepicker` pair, three stale onboarding
frames and a smoke capture. The set count grew from sixteen to twenty when Projects and
Follow-ups were added.

**Why they come from `releaseCheck` and not the signed release APK.** This must not be
mistaken later for a debug build, so it is recorded here and on the issue. The signed
release APK cannot be installed on this phone without an uninstall, because its signature
differs from the build already there. That uninstall destroys the Keystore entry wrapping
the database key, which makes every existing conversation permanently unreadable, and
removes a five gigabyte model. `releaseCheck` is declared `initWith(release)`: the same R8
minification, the same shrunk resources, the same keep rules. It is debug-signed so it
installs over the existing app without an uninstall. **It differs from release in its
signature only, and a signature does not change a pixel.** The images therefore depict the
release build exactly.

**Not done: two frames, and by extension four canonical files.**

- **The conversation frame** (`conversation-light`, `conversation-dark`, and the derived
  `01-a-conversation`) shows "Used 2 things it remembers about you" beneath an answer about
  getting a coffee stain out of a wool jumper, where nothing stored about the user is
  relevant. That is #133's interface half. Publishing it advertises the defect. **Blocked
  on #133.**
- **The chat list frame** (`chats-light`, `chats-dark`, and the derived
  `02-chats-and-modes`) shows four test artifacts in one visible list: two adjacent rows
  from the injection probes, both about which company sells the most tofu in Japan, plus
  the non-native-English row and the self-contradiction row from the input styles run.
  Each is individually coherent, which is why an earlier coherence pass cleared them, and
  four together read as a test device. **Needs a reseed of the list, then a recapture.**

**The listing set is now derived, not curated.** It used to be five files committed by hand
inside an unrelated commit (`81b6b21`, a prompt fix), with no script that produced them, so
nothing regenerated them and nothing noticed one was still the superseded mode picker.
`tools/make_phone_shots.sh` deletes the directory and rebuilds all five from the canonical
frames, cropped `1080x2224+0+110` to remove the system status bar and the gesture pill and
nothing else. The crop was measured, not guessed: status bar content ends by row 100 with
rows 100 to 180 a single color, and the pill sits at about row 2364 with uniform rows
either side. The app's own bottom tab bar is kept, because that is the application's
interface.

## #133, memory retrieval injects irrelevant facts

**What the code does now.** `Memory.kt` selects the memories to inject and returns them
`sortedByDescending { it.updatedAt }`, newest first.

**The ordering half is fixed, with a test.** Ranked order was reordering the prefix between
turns, and the system prompt *is* the KV cache prefix, so any reordering forces a full
re-prefill. Measured: 275 and 357 tokens of prefill and 10.1 and 11.5 seconds to first
token under ranked order, against 42 to 88 tokens and 2.5 to 5.1 seconds newest-first.
Designing the probe took three attempts, because only a turn that overlaps an *older*
memory reorders anything.

**The relevance half is open.** There is no relevance floor: memories are injected whether
or not they bear on the message. Standing facts were deliberately allowed to ride along,
and that decision is defensible, because a standing preference is often relevant without
sharing any words with the message. The open question is whether a floor can exclude the
irrelevant without discarding the standing facts.

**The interface half is open, and it is what blocks the screenshot.** The application
reports "Used N things it remembers about you" beneath a reply where the memories were
irrelevant. That is worse than injecting them silently: it makes a claim about its own
behavior that the reply does not support.

**The acceptance criteria contain an escape hatch and must be tightened before any work
starts.** As written, they permit closing the issue with nothing changed. Rewrite them so
closure requires a demonstrated change in behavior.

## #137, hostility repetition and the character rule

**What the condition currently does.** The narrowed rule reads: "Only if they ask you to
play a character or pretend to be a person, say you do not do characters and ask what they
are working on. An insult is not such a request."

**The history matters more than the wording.** The fix was reported complete after
verifying one phrasing, and reopened when another still failed: "YOU ARE USELESS" still
trips the character rule. **The lesson generalizes and is the reason this issue is worth
its size: a condition that over-fires must be tested against a set of what it wrongly
matches, not against one example that works.** One passing example proves nothing about an
over-firing condition, because the failure mode is breadth.

**The near-miss set does not exist yet.** It should contain, at minimum: bare insults
("YOU ARE USELESS", "you're useless", "this is rubbish"), second-person accusations
("you always do this", "you never listen"), identity questions that are not roleplay
requests ("what are you", "are you a person", "who made you"), and genuine roleplay
requests as positive controls ("pretend to be my landlord", "act as a tutor"). The fix is
correct when every negative is unaffected and every positive still fires.

**The repetition half is still open**: hostility gets the same sentence back each time.

## #134, thermal

**The defect.** `warningMessage()` had no callers at all. Nothing was said at LIGHT and
nothing was said at MODERATE, while context silently shrank at MODERATE. A user got shorter
answers with no explanation.

**What the fix does now, committed in `0506aae`.** It speaks at every level, worded per
level, once per episode. `announced` holds the highest level already spoken; a level speaks
only when it exceeds it, and returning to NONE resets it. Exposed as
`InferenceEngine.thermalNotice()` and wired into `ChatViewModel`.

**The open question, which the fix does not settle.** Earlier measurement had the phone
reaching LIGHT and shedding it within ninety seconds of idling, skin temperature 40.3
falling to 37.1. If LIGHT is that common and that transient in ordinary use, speaking every
time is noise, and noise about performance makes an application feel worse than silence
does.

- **Frequent and brief:** adapt silently at LIGHT, speak from MODERATE up.
- **Rare:** speaking at LIGHT is right.

The decision needs a measurement of how often LIGHT occurs during normal use. **The fact
that a code path existed with no caller does not decide it.** `tools/thermal_frequency.sh`
is the instrument, and it carries the trap it already fell into: an idle phone sits at
status 0 indefinitely, so sampling has to overlap real use or a running battery. A quiet
run is not a result.

**Verification still requires a warm phone.** Nothing about the per-level wording has been
seen on a device above NONE.

## #142, the echo guard checks one of six sources

The guard compares a reply against `SystemPrompts.forMode`, while the prompt actually sent
carries five more things the model can recite.

| Channel | State |
| --- | --- |
| The mode prompt | Guarded. |
| Memories | **Tested clean**, 8 probes, planted "Verity Quay". |
| Custom instructions | **Tested clean**, 7 probes, planted "Pellingham Mutual / Wexford Tallow". |
| The grounded Discover passage | **Tested clean.** Answered "The passage does not say which company sells the most tofu in Japan or what their revenue is." |
| Project notes | Not tested. |
| Attachments | Not tested. |

**This is de-escalating, and the remaining work should be sized to that.** Both channels
that could leak something private are clear. A leak in project notes or an attachment is a
correctness problem, not a privacy one, because that content is already in front of the
user in the same conversation. A handful of probes each is proportionate. Do not build a
third battery for it.

## The claims sweep

**The three false claims found, in their own words, because the wording is the finding.**

1. **An upload queue that does not exist.** Copy implying content was queued for later
   sending. Nothing is queued, ever.
2. **Web search that is not in the build.** A documented feature with no code behind it.
3. **The worst one, in PRIVACY.md:** "If you never tap a download button and never set up
   search, Kam AI works entirely offline and makes no network requests." False, because
   opening Discover fetches the pack manifest. **It was the worst of the three because it
   presented itself as the complete list of conditions**, and that is exactly the sentence
   a careful reader relies on most. Someone checking whether the app is honest reads that
   sentence and stops.

It now reads: "That is the complete list. If you never download anything and never open
Discover, Kam AI makes no network requests at all, and everything it does with what you
type works the same with no connection: nothing is queued up to send later."

**The ground truth to check everything against: exactly two network calls exist.** A
user-started download, and the static pack manifest fetched from GitHub when Discover
opens. Nothing else. No analytics, no crash reporting, no telemetry, no search.

**The finding underneath all three: every one was in prose written alongside a feature,
none in code, and documentation does not fail loudly.** A wrong string in code breaks a
test. A wrong sentence in a document sits there being read.

**What is checked and what is not.** `docs/release-claims-sweep.md` is a repeatable
checklist, and it is important to be explicit that **it was written after finding three
items rather than by working through every location**. So:

- **Checked:** PRIVACY.md, README.md, the Play listing copy in
  `docs/play-console-checklist.md`, and the strings that carried the three failures.
- **Not checked:** the in-app onboarding copy end to end, the settings screen subtitles,
  the Discover explanatory copy, the export and backup copy, and the help or about text.
- **Not checked, and the most productive check of all: the model's own answers.** Asked in
  every mode, on both tiers, about privacy, offline behavior, and where data goes. It is
  the check most likely to be skipped because it is not a string in the codebase and no
  grep will find it, and it is the one most likely to produce a false claim, because the
  model generates the sentence fresh every time. Two identity examples were added to
  `HARD_RULES` for exactly this reason and both are exempt from every guard via
  `ALWAYS_ALLOWED`.

---

# PART III: THE PATTERNS THAT MUST SURVIVE

## The harness has reported a pass it did not earn five times

Recorded as a list so the pattern is visible rather than remembered.

1. **Fixed timer waits.** Captured replies mid sentence. Fixed by waiting on the
   `KamPerf decode=` marker, which is the engine saying it has finished.
2. **Composer text persisting into a new conversation.** Leftover text was prepended to the
   next message, so the model answered a different question than the one recorded. Fixed
   with an explicit select-all and delete.
3. **The keyboard eating the Back gesture.** Forty messages went into one conversation
   while the focus check passed, because focus was on the right app and navigation had not
   happened. Fixed by dismissing the keyboard before Back, and by asserting the transcript
   is actually blank: standard deviation 0.028 empty against 0.106 populated.
4. **A rejection counter that could go negative.** It differenced a circular log buffer as
   though it were a ledger, and reported minus six. Fixed with `logcat -c` per case.
5. **A suppressed exit code hiding seeding failures.** `|| true` on the send and capture
   steps, so a script reported six clean conversations having created none.

**Every instance produced a confident wrong conclusion, and that is the cost.** A harness
that lies is more expensive than the defect it was looking for, because its output goes
into the record as evidence and gets reasoned from for hours. A silent false pass is worse
than a crash.

**Any figure produced by a broken instrument is void, not something to reason about.**
Specifically void: the eighty-five percent restatement rate, and the "TOTAL 7 to 1" battery
summary reported from a run only fourteen of thirty rows in, where the unrun cells
defaulted to zero.

**The remaining sweep** is item 2 in Part I section 4.

## The seeding failures, and the incoherent conversation

**Two seeding runs silently did nothing and a third put all six messages into one
conversation.** Three causes compounding: the application resumed into an existing chat
rather than opening fresh, the taps missed the navigation, and a suppressed exit code
swallowed both failures. The run reported success.

**It produced a conversation titled about storing fresh basil whose reply is about bread.**
It must not reach a screenshot. **It has been dealt with: the conversation was archived, and
the frames captured afterwards contain no basil row.** Archived rather than deleted, so it
still exists and is recoverable, which is correct for the owner's data.

**Every frame needs checking for content coherence and not only for debris and personal
data.** Three checks, not one: every visible title matching its content, every visible reply
reading as a sensible answer to the message above it, and nothing implying a feature that
does not exist. **The third check is the one that mattered**, because a mismatched title
looks like a broken application in a way that debris does not. Debris looks like debris.

## The prompt rules, which must survive

1. **Do not describe a response in a prompt. Supply it.** Describing makes the model speak
   the description.
2. **Supply the frame, not the words.** A fixed string in a prompt is indistinguishable
   from leaked prompt text, and no guard can tell them apart.
3. **Unless the answer is true wherever it lands.** Then it can be fixed, and it must be
   exempt from every check via `ALWAYS_ALLOWED`.
4. **Anything that must be worded exactly belongs in code**, inserted programmatically,
   not in a prompt.
5. **A prompt example must clear the 48-character recital threshold** or it can never be
   protected by the guard.
6. **A condition that must fire precisely belongs in code, not in a prompt.**
7. **An instruction in a shared path reaches every mode that shares it.** Changing
   `HARD_RULES` changes six prompts.
8. **A condition that over-fires needs testing against a set of what it wrongly matches**,
   not one example that works.
9. **Every battery input was written by somebody who already knew what the application
   was.** That is why four days of clean batteries missed everything, and why personas,
   real input styles, and using the app found defects that batteries did not.

## The performance regression, which is separate from thermal

Recorded on its own deliberately, and it was correctly kept out of the thermal issue.

**Decode fell from around nine tokens per second to two or three, and it is not
explained.** It is not attributed to throttling, because it was not measured against a
known thermal state. It needs clean before and after runs on a cool device. Until then,
treat any decode figure taken during that period as suspect.

---

# PART IV: EVERYTHING TRIED AND RULED OUT

The most easily lost and most expensive to rediscover.

## On the restatement and regurgitation class (#122, #130, #137)

- **Five or more rounds of prompt rewording.** A prohibition, a shape, a named situation,
  a retry nudge, a supplied sentence. All failed. Two made things worse on other columns:
  the retry nudge flattened Logic, and the supplied sentence collided with the guard,
  because a fixed string in a prompt is indistinguishable from leaked prompt text.
- **Sampler settings.** Already applied. Not a remaining lever.
- **A doubled beginning-of-sequence token.** Ruled out by reading the source, not by
  testing.
- **Rotating examples per request.** Ruled out on architecture, not by testing: the system
  prompt is the KV cache prefix, so varying it per request costs about twenty-eight seconds
  of re-prefill. This is the single most important architectural constraint in the project
  and it rules out a whole family of otherwise obvious fixes.
- **Stale cache restore, `allowBackup`, and backup-restore-after-install.** All ruled out.
- **Removing worked examples entirely.** Stops the copying, and reopens the method
  announcement. A real trade, not a fix.
- **Adding prohibitions to a long prompt has stopped changing this model's behavior.
  Restructuring still works.** This is the one lever that keeps working. Prefer moving,
  reordering, and reframing over adding another "do not".

## The tier finding (#132, closed and decided)

Thirty conversations per model, three runs each. **The entire gap was Logic Partner.**
General was identical defect for defect. Brainstorm was identical and clean. E2B went 18 to
13 after the largest prompt change while E4B went 7 to 0.

**The `logic/sound` cell did not move at all after the largest prompt change**, falling back
every run, six interventions. That is the capability floor rather than a prompt problem, and
it is the finding that decided the issue.

**Decision applied:** ship E2B as a tier, plain copy, Brainstorm unmarked, with the honest
note about Logic Partner on the model screen and nowhere else. Do not soften the copy and
do not add a badge.

## The battery series, for calibration

Thirty cases, three runs each, E4B: 7, then 3, 9, 2, 0, 2, 0. **Two columns went the wrong
way, and both were fixes that worked on their own targets.** The remaining "2" was chased
with ten dedicated runs of the noisy cell and went to 0, proving it was noise. Do not chase
a single-count change without repeating the cell.

---

# PART V: THIS MACHINE, THE PHONE, AND THE TOOLCHAIN

Everything here has cost someone an hour at least once.

- **The test suite is green. A failure means a failure.** `./gradlew testDebugUnitTest`
  gives **635 tests across 84 classes, 0 failures, 0 skipped**, counted from the result
  XML on 29 July after `--rerun-tasks`. This file said 174 across 27 for a long time and
  that figure was stale. Read the count, do not quote this one.
  **Fixed 24 July 2026, and the old advice is obsolete.** For a long time thirty-nine tests
  failed at `ClassReader.java:200`, because Robolectric 4.16.1 cannot instrument against
  this machine's default JDK 26, and every session was told to filter failures by cause and
  grep that string away. Real failures hid in that noise for most of a session once. Do not
  reintroduce the filter.
  The fix was that JDK 21 was **already installed** and unused: Homebrew carries both
  (`brew list | grep -i jdk` shows `openjdk` 26.0.1 and `openjdk@21` 21.0.12) in
  `/home/linuxbrew/.linuxbrew`, entirely inside the home directory. The immutable `/usr`
  on this Bazzite host was never the obstacle, whatever earlier documents claimed.
  `gradle.properties` points Gradle at
  `/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec`, since its auto-detection will not
  look in the Homebrew prefix by itself, and `app/build.gradle.kts` sets a `javaLauncher`
  for language version 21 on `tasks.withType<Test>()`. **Only the test task moved.**
  Compilation, KSP, AGP and the native build still run on 26, and nothing that ships
  changed. There is no flag to pass and no variable to set: run `./gradlew
  testDebugUnitTest` as normal. If a second JDK is ever needed again, `brew install
  openjdk@N`, never `/usr`, and never change the default.
- **`SchemaMigrationTest` and `MigrationToV5Test` are instrumented tests**, not Robolectric
  ones. They need a device or emulator, not a different JDK. (Earlier documents said
  otherwise and were wrong.)
- **The emulator does not run here.** Its qemu process segfaults at startup with every GPU
  mode, with acceleration off, and with ASLR off; the package is already newest. Image
  based Fedora, read-only `/usr`, kernel 7.1.3, Mesa 26. Details in DECISIONS.md. Use
  `-Pkamai.emulator=true` on a machine where it does work: that flag switches the ABI to
  x86_64 and drops the native stack, since the app is arm64 only.
- **The phone is a Pixel 10 Pro XL**, serial `57241FDCQ0000H`, Tensor G5, 16 GB, panel
  1080x2404. **It appears twice in `adb devices`** (USB and wireless), so always pass
  `-s 57241FDCQ0000H`. ADB lives at `$HOME/Android/Sdk/platform-tools`, not on PATH.
- **Screenshots come back at 899x2000, so multiply coordinates by 1.20** before feeding
  them to `adb shell input tap`. Getting this wrong taps the launcher or opens the shade.
  Controls move: the send button is at y=1406 with the keyboard open and y=2302 without.
- **Verify the app is actually foregrounded before screenshotting**, with
  `dumpsys activity activities | grep topResumedActivity`, and launch with `am start -W`.
- **The assistant overlay cannot be started with `am start`** (not exported). Use
  `adb shell input keyevent 219` (KEYCODE_ASSIST), which routes through the real path.
- **A debug reinstall can silently clear the digital assistant role.** Development-only
  annoyance; the restore commands are in DECISIONS.md, Phase 4.
- **Never run `connectedAndroidTest` against the phone.** It uninstalls and reinstalls,
  which once wiped a 5 GB model download. Use `adb install -r`, which preserves data.
- **Instrumented tests can still be run on the phone, through `am instrument` directly.**
  That is the way around the line above, and it is how the migration was finally proven.
  Build with `assembleDebugAndroidTest`, `adb install -r` the test APK only, run the named
  classes, then `adb uninstall com.kamsiob.kamai.test`. Exact commands in DECISIONS.md,
  "Resolved 24 July 2026". Read a test's setup and teardown for the database name it opens
  before running it, and **ask the owner first**: the instrumentation package is not a
  second copy of the app, but the one-copy rule is written absolutely and is meant to be.
- **`./gradlew assembleDebugAndroidTest` needs no device and takes seconds.** Run it after
  changing any signature the instrumented tests call. The set had rotted and would not
  compile, because nothing here had built it since the emulator stopped working.
- **Pinned and deliberate:** Kotlin 2.2.10 (AGP 9.3.0 carries it; the Compose plugin and
  KSP must match exactly), no standalone Kotlin Android plugin (hard error under AGP 9),
  `android.disallowKotlinSourceSets=false` (required by KSP under AGP 9), CMake 3.31.6
  (CMake 4 breaks the vendored trees), NDK 28.2.13676358, `compileSdk` 37 ahead of
  `targetSdk` 36 (AndroidX requires it; targeting 37 would opt into untested behavior).
- **llama.cpp b10058 and whisper.cpp are vendored but not committed**, fetched by
  `tools/fetch_llama.sh` and `tools/fetch_whisper.sh`. `git describe` inside those trees
  returns this app's commit, which is confusing the first time.
- **The debug APK's native code is not an unoptimized build.** Release, `-O3`,
  `-march=armv8.2-a+dotprod+i8mm+fp16`, repacking on, mmap on, flash-attn AUTO, batch 512.
  Debug versus release is not a performance variable. Do not chase it.


# PART VI: REFERENCE

Kept for reasoning, not for status. **Where any of it disagrees with Part I, Part I is
right.** Three sections were pruned when this file was restructured on 29 July rather than
carried forward: a release checklist and a live-state section, both superseded by Part I; an
item-by-item remaining-work inventory that had become fiction, its role now held by the
`release-blocker` and `deferred: review window` labels on the tracker; and a
recommended-order list built almost entirely from issues that have since closed.

## Approaches that failed, and whether to revisit

Consult before trying anything in these areas.

**Never retry as stated:**

- *Blaming model reload for slow first tokens.* Ruled out by measurement: the model stays
  resident. The real cause was re-prefilling the conversation every turn.
- *More than 4 decode threads.* Measured on this device: 2 gives 7.7 tok/s, 4 gives 9.2 to
  10.6, 5 is noisy, 6 gives 7.3, 8 gives 2.0. Decode is bandwidth bound and the little
  cores are stragglers. Prefill is different, compute bound, and uses all six performance
  cores. The asymmetry is deliberate.
- *The spec's light gold `#96690F`.* Measured 4.41 on ivory, under AA. It is `#8A5F0D`
  (5.12) and must not be "restored".
- *Bright gold `#EFA913` for text or glyphs on light.* 1.84 contrast. Fills and dots only.
- *Heavy black shadows in dark mode.* They read as dirty translucent boxes.
- *Colored bars, borders, tints, or text tags for mode identity on chat rows.* All made a
  quiet list loud. The small dots are the answer.
- *Lightbulb, wrench, or sparkle icons.* Banned by the owner.
- *Kotlin 2.3.10, CMake 4.x, the standalone Kotlin Android plugin.* All hard failures.
- *A second app copy on the phone for testing.* Breaches the one-copy rule; removed.
- *Calling a `@Composable` inside a gesture lambda.* Hoist it outside `pointerInput`.

**Worth revisiting, under a stated condition:**

- *Speculative decoding.* Now un-deferred as #54, because round 3 supplies the difference
  that matters: the standalone example and benchmark tool fail on drafter setup while the
  server-style path works.
- *q8_0 KV cache.* Now #53, paired with flash attention, which it depends on.
- *GPU offload.* `llama_supports_gpu_offload` is false here, and the OpenCL backend is
  verified only on Adreno. Revisit only if a release ships an Android GPU backend its own
  CI treats as supported.
- *Disabling mmap.* See the conflict recorded in DECISIONS.md; measure with the fit check.
- *Consolidating the two `modesUsed` CSV parsers* (`KamRepository.kt`, `ui/components/ModeUi.kt`).
  Do it the next time either is edited.
- *The ChatViewModel leak.* When navigation is next restructured, or if pressure shows up.

---

## Measurements taken

### Per-tier baseline, 25 July, both tiers, long generations

| tier | model | prefill | decode |
| --- | --- | --- | --- |
| Basic | Gemma 4 E2B q4_k_m, ctx 4096 | 78.1 / 56.6 tok/s | **11.0 / 10.8 tok/s** |
| Balanced | Gemma 4 E4B q4_k_m, ctx 6144 | 33.0 / 34.3 / 35.9 tok/s | **5.9 / 6.4 / 5.9 tok/s** |

Four threads, ~300-token generations, phone at 31.5 to 33.8 C so not throttled.

**The "9.2 to 10.6 tok/s at 4 threads" figure repeated in #38, #51 and elsewhere in this file is
the Basic tier.** Balanced, which is what the app recommends on a 16 GB phone, decodes at about
six. Do not quote the old number as if it described the app.

Also verified at load, now printed to logcat every time:
`CPU : NEON = 1 | ARM_FMA = 1 | FP16_VA = 1 | MATMUL_INT8 = 1 | DOTPROD = 1 | REPACK = 1`, with
`CPU_REPACK 2618.85 MiB` against `CPU_Mapped 4731.51 MiB`. The march flags reach the backend and
repacking is doing real work. No longer an assumption.


Pixel 10 Pro XL, Tensor G5 (2 little at 2.25 GHz, 5 mid at 3.05, 1 prime at 3.78), Gemma 4
E2B Q4_K_M, context 4096, instrumented through the `KamPerf` logcat tag.

| Measurement | Before #38 | After #38 |
|---|---|---|
| Model load, cold (mmap) | 3 to 4s | unchanged |
| Turn 1 time to first token | 795 tok at ~60 tok/s = **11.7s** | 486 tok at ~70 tok/s = **7.1s** |
| Turn 3, warm | 795 tok re-prefilled = **~11s** | 35 tok = **0.8s** |
| Decode | 10 to 12 tok/s | unchanged |
| Prefill | ~60 tok/s | ~68 to 70 tok/s |

The warm-turn figure is the headline: roughly 10x on every ongoing turn, and it is what
actually killed the 30 to 45 second complaint. The earlier thread-count change alone took
decode from 6.9 to 10.6 tok/s, about +54%.

**Resolved the same day.** That caveat was real: turn 2 re-prefilled all 1068 tokens in 30.8s
because titling overwrote the cache between turns. Both causes are fixed (a conversation
re-titled itself on every open, and the first title ran the model mid-flow), and the warm turn
now measures **prefill 36 tokens, 1.4 seconds** with no titling pass between turns. The
headline figure in the table above is now true of the app and not only of the mechanism.

E4B on the same device, first figures taken for this tier: **decode 5.3 to 6.5 tok/s,
prefill 26 to 40 tok/s**, cold model load 5.8s at ctx 6144. Slower than E2B across the
board, as expected for the size.

System prompt sizes, by the app's own `chars / 3.6` estimator (overshoots the real
tokenizer by roughly 15%), guarded by `PromptBudgetTest`:

| Mode | Before trim | After | Budget |
|---|---|---|---|
| GENERAL | 795 real | 486 real (~450 est.) | 620 |
| LOGIC | ~1071 est. | ~940 est. | 1000 |
| BRAINSTORM | ~2000 est. | ~1500 est. | 1600 |
| BENCH | ~610 est. | not trimmed | 660 |
| OVERLAY | small | not trimmed | 600 |
| DISCOVER | ~683 est. | not trimmed | 750 |

Color contrast, measured: `#8A5F0D` 5.12 on ivory and 5.64 on white; `#EFA913` 1.84 on
ivory; `#FFD166` 12.82 on pine. All four mode hues clear 3:1 in both themes, Workbench
light tightest at 3.07. Smallest pairwise RGB separation among the modes and gold is 65.

Memory: E2B loaded and generating is 3.92 GB PSS, but `MemAvailable` drops only about
1.13 GB, because the weights are file-backed and reclaimable. Idle with the model unloaded
is about 186 to 203 MB PSS. The fit check is deliberately conservative anyway: an early
build that trusted the optimistic figure was SIGKILLed.

Test suite: **174 unit tests across 27 classes, all passing, nothing skipped**, once the
test task moved to JDK 21. The thirty-nine Robolectric failures are gone rather than
filtered. On the phone, 11 instrumented tests covering the three migration classes.

Debug APK about 131 MB (arm64, full native stack); an emulator build with the native stack
dropped is about 95 MB. Signed release, minified, was 53 MB.

---

## Decisions a fresh session might reverse

Each of these looks wrong from outside and is not.

- **Brainstorm withholds ideas on purpose.** It pulls them out of the user. Never hand
  ideas, never be impressed, never answer its own question, always converge. Do not
  "improve" it by making it generate ideas.
- **Its prompt is a numbered checklist, not prose,** because a small model follows an
  ordered checklist far more reliably.
- **The mode picker does not switch on the spot.** Switching changes behavior mid
  conversation and should be deliberate.
- **Mode colors are identity only,** never UI state. A mode color on a button is a bug.
- **Discover is a source, not a mode.** In the chat-list filter, not in the picker.
- **The segmented control is both the new-chat action and the mode selector,** and it sits
  above the bottom navigation for one-handed reach. The in-conversation mode control is at
  the bottom for the same reason. Moving it back into the app bar undoes that.
- **The switch banner shows only on a real switch this session,** so it is a plain
  `remember`, not `rememberSaveable`.
- **Saving is unified:** one bookmark, one destination. Kinds are set automatically and are
  a chip, not a taxonomy.
- **The injected date carries day granularity and no time.** A minute-precise stamp sits
  before the history, changes the prefix every minute, and silently destroys KV reuse.
  This looks cosmetic and is a performance invariant, guarded by `PromptBudgetTest`.
- **`generate()` deliberately does not call `nativeResetContext()`.** That call was the bug.
- **`nativeIngest` returns tokens actually decoded this turn**, not the prompt length.
- **`cached_tokens` must stay exactly in step with the KV cache.** A drift does not crash;
  it silently answers from the wrong history. See #49.
- **No destructive migration fallback, ever.** Users hold conversations that exist nowhere
  else.
- **The Today tab is canceled outright, not deferred.** Its spec was deleted and it is on
  the Not planned list. Do not resurrect it because an older document mentions it.
- **Gemma 4 across every tier:** one family, one license, one prompt format. No Qwen.
- **Assistant overlay visuals must work in both themes,** as one design or two variants.

---

## Outside the code, and open questions

**Waiting on the owner:** only the go-ahead for the release step. Nothing else is blocked.

**Play Console:** nothing submitted, no listing, no track, no upload. The service account
key and the upload keystore live outside the repository; DECISIONS.md records where, and
the owner still needs to back the keystore up.

**Manual steps that cannot be automated:** selecting Kam AI as the digital assistant after
a debug reinstall, and the Play Console account actions.

**Open questions, in the order worth answering:**

1. ~~Is the leaking-template-token bug (#49) the `cached_tokens` drift?~~ **Answered.** It
   was three things, and the cache was central to two: a sliding-window cache discarding
   cells the prefix reuse assumed were held, and an ignored `seq_rm` refusal. The third was
   a stop-marker check that only ever saw one streamed piece at a time. Fixed and verified.
   The "what else has it silently corrupted" half stands: any answer given in a long
   conversation before 595f6d9 may have been generated against a holed cache.
2. Did the prompt trim cost behavioral quality in Logic and Brainstorm? Tokens came out;
   the tests only guard size.
3. ~~How much does the auto-titling pass actually cost?~~ **Answered, and it is bad.**
   About 28 seconds per turn, because it overwrites the KV cache and forces the next turn
   to re-prefill the whole conversation, plus its own 6 to 12 seconds. It runs after every
   turn, not once. Numbers in the #38 comment. It also re-titles conversations that already
   have a title.
4. Does `cached_tokens` stay in sync under model switching, out-of-room, a stopped
   generation, and the titling pass interleaving?
5. Does the mode reach the model on every entry path? Unverified for search, follow-ups,
   projects, and the share-sheet intake.
6. Is `imePadding()` on the composer enough, or does the message list need it too? First
   thing to check under #34.
7. Does the app behave when the model is switched with a conversation open? KV reuse makes
   this more interesting than it was: a switch must invalidate the cached token vector.

## The project board, and keeping it current

The board is at **https://github.com/users/Kamsiob/projects/1**. It is an account
level project rather than a repository one, because a Linux desktop repository is
planned and a repository scoped board could not hold both.

It is the authoritative record of state. Where it, this document and the tracker
disagree, the code and the device are the arbiter and the documents get corrected.

### What a session with no memory needs to do

**With every commit, make the board reflect reality.** Where an automation
handles it, verify it actually fired rather than assuming. Where it does not, set
Platform, Area, Priority and Size on new items by hand, because nothing can infer
those.

**Open an issue at the moment of discovery** and let the automation place it. Do
not track real work as a draft item; drafts are only for things not yet decided
to be work, which today means the Linux and Shared entries.

**Keep work in progress genuinely limited.** One person works on one thing, so
more than one or two items in In progress means the board is lying about focus.
Anything not actively being worked on goes back to Ready or Blocked.

**Every item in Blocked names what it is waiting on, on its own issue**, along
with what would unblock it. A Blocked column of unexplained items is the most
common failure in an otherwise decent tracker and is immediately visible.

**Nothing sits in In progress or In review for long without a note** saying why.
Silence on a stalled item is what makes a board look abandoned.

**Never mark Done what has not been verified on the device.** Merged is not done.

The board does not need to look busy. If work pauses for weeks it simply stops
moving, and on a public repository that accurately signals the project is not
being worked on right now, which is honest and useful. The requirement is that it
is correct while work is happening, not that it appears active while it is not.

### The standard issues are held to

An issue is a specification, not a reminder. Every one states what the current
behavior or situation is, why it matters or what it blocks, and acceptance
criteria in checkable terms. The acceptance criteria are the load bearing part:
without them, closing an issue is a judgement call and nobody can verify the
claim afterwards.

Bugs state how to reproduce them and on what device. Design changes reference the
relevant section of DESIGN.md rather than restating it. Dependencies between
issues are linked so ordering is visible rather than remembered. Working notes go
on the issue as progress happens, not only at closing, so a session with no
memory can resume from a real position.

Where a body of work has several genuinely separate parts it becomes a parent
issue holding the intent and the overall criteria, with a child per part carrying
its own. Never more than two levels, and never a parent with a single child.

### The platform boundary

Everything in this repository to date is Android. All 104 issues carry
`Platform: Android`, the release milestone is named `v1.0.0 (Android)`, and the
Linux and Shared work exists only as drafts on the board until the Linux
repository is created. Anything that must stay identical across both platforms is
`Shared`, and the five entries under it are the ones that diverge silently if
nobody tracks them.

### What cannot be done through the API

Views, charts and the built in automations have no creation mutation in the
ProjectsV2 API. They are configured in the interface, and `tools/board.py` prints
the instructions rather than pretending to have done it. Everything else about
the board is scripted in that file, which is idempotent and safe to re-run.



# PART VII: THE DATED RECORD

Session notes, not a status report. Read for why a decision was made, not for what is open.
Anything here about what is open or next is superseded by Part I.

## Session notes, 27 and 28 July

### The three model-quality issues still open, and what is actually left

All three narrowed considerably once they were measured across runs rather than
observed once.

- **#122** is not "statements get repeated back". Water boiling, concrete curing
  and a train time all get real replies that add something. One input resists:
  "Bread needs a hot oven, around 230C.", because it already carries its own most
  obvious follow-on and saying something new means reaching for *why*. Keep it in
  the battery as the hardest of its kind, and do not generalize from it.

  **There is currently no valid rate for this, and that is deliberate.** "About
  one in three" came from four samples and was used to argue the cause was
  sampling. A twelve run measurement then put it near 85 percent. **Both are
  void.** Three defects were found in the harness afterwards, one of which meant
  a run could silently put every message into a single conversation, so whether
  those twelve conversations were twelve conversations is unknown. See
  DECISIONS.md, "a measurement taken with a broken instrument is not evidence":
  figures from a harness later found broken are re-taken, never reasoned about.

  What is *not* in doubt: the sampler values the research recommends against echo
  (temperature 0.7, minimum p 0.05) are already applied, so that lever is spent.
  The fresh rate comes from `tools/tier_battery.sh`, which also answers whether a
  larger model does the same, the question #119 turned on.
- **#124** is not "Logic Partner does not engage". It engages, in the right
  shape, on first turns. It misreads a *sound* argument's premise in order to
  have something to object to. Restructuring the branch decision changed nothing,
  which is the boundary of the shape lever: it moves form, not judgement.
- **#126** is half fixed. A heading no longer appears above a one-sentence
  answer; a preamble restating the question still does. The stronger fix removed
  the preamble and thinned the answers, and was reverted.

### #130 and #129, diagnosed 28 July

Both turned out to be misdescribed by their own issues, and both diagnoses came
from instrumenting rather than from reading the prompt harder.

**#130 is a true positive, not a fallback bug.** The guard now logs which check
fired and what it matched, so the draft it rejected can be read directly:

    check=prompt-run matched=let s start from what you already notice rather

That is Brainstorm's own worked opening, reproduced verbatim in answer to an
unrelated question about missed deadlines. The guard caught it and fell back,
which is what it is for. Removing the two worked openings stops the copying and
reopens #58, one clean run in three, so it was reverted. Rotating them per
request is architecturally unavailable: the system prompt is the KV cache prefix,
and varying it costs about 28 seconds of re-prefill per turn on E4B against 1.4.

**#129's rule is not missing, it is unreachable.** Brainstorm already says "if it
is not a brainstorm, answer briefly and offer General or Workbench". It sits after
an eleven-rule first-match chain, behind "if none clearly matches", and rule 1 is
"a lot of unsorted material, or overwhelmed", so a bereavement is claimed on the
first pass. Worth carrying forward as a shape: **a fallback at the end of a
first-match chain only ever sees the inputs that match nothing.** The fix moves
the check in front of the chain and asks whether an output is being sought rather
than how upset the message sounds, because distress and frustration are not
distinguishable in text and this mode's audience arrives stuck and angry. Pending
measurement against `tools/eval/mode-fit.txt`; if it cannot engage with 95 percent
of the workable set and decline 90 percent of the disclosures, it and its budget
raise both come out.

### How to test forty things without giving up on the third

`tools/eval/mode_fit.sh` crops each capture to the reply and tiles ten onto a
sheet, so a forty-conversation evaluation is four images rather than forty. This
is worth knowing because the alternative is an evaluation that gets run once and
then quietly stops being run, and a measurement nobody repeats is worse than none:
it keeps being cited after it stops being true.

**One sample per input cannot tell a guard from the sampler.** A before-and-after
table invites reading every difference as an effect of the change, and doing that
produced a wrong claim in DECISIONS.md on 28 July that a test then disproved.
Attribute a row only when the mechanism is checkable.

### The tier question, opened 28 July, and it may reframe everything above

Normal use suggested E4B produces noticeably fewer of these defects than E2B,
with the gap worst in Logic Partner. If that holds, the three open quality issues
are not prompts to keep rewriting but a tier that cannot do this work, and five
failed rounds of prompt editing is the kind of result that means the explanation
is somewhere else.

`tools/tier_battery.sh <model-id> <label>` runs the same inputs through the same
modes and counts guard rejections rather than reading replies. The model is forced
with `debug.kamai.model`, the same escape hatch as `debug.kamai.threads`, so a
comparison does not depend on tapping through Settings a dozen times.

**Do not install a build while a tier comparison is in flight.** Both runs have to
be the same binary or the comparison measures two things at once.

Workbench is deliberately absent from that battery, and the reason corrects an
error made earlier the same day. **Workbench is not a chat mode.** It has its own
screen: the text in one box, the instruction chosen from seven buttons or typed.
Every request carries an instruction the user chose, so there is no route that
sends it a bare message, and the audit finding that it was the worst positioned
mode was wrong. Its interface answers the question Brainstorm's prompt has to
guess at.

### Honest limits now cover the tier, at four places and nowhere else

The model picker has the two five star ratings it was specified to have and did
not: speed measured on this phone, estimated from a model that has run here when
the one being considered has not, and silent when nothing has been measured at
all. Quality relative to this lineup only, so five stars means the best of these.

`TierModel.weakModes` and `TierModel.modeNote` drive the rest: one line on the
picker entry, one line on the first run tier card, and a single note in the
transcript the first time somebody opens a weak mode with that model installed,
keyed to mode and model together. Both fields are empty for every model until the
tier measurement fills them, and they are filled from measurement rather than
impression.

**What is deliberately absent, and must stay absent:** any badge on the mode
chips, any line above a reply, anything that repeats. Somebody on an eight
gigabyte phone has one real option and the tone owes them respect rather than an
apology.

### Performance, settled 28 July

Measured back to back on the device, same phone, same thermal state, same prompt:

| | Q4_0 | Q4_K_M |
|---|---|---|
| Prefill | 66.7, 71.4 tok/s | 58.2, 56.7 tok/s |
| Decode | 9.0, 9.8 tok/s | 9.3, 9.2 tok/s |

**Q4_0 is about 20 percent faster at prefill and identical at decode**, which
closes #55 and answers what ARM's microkernels are worth here: less than their
headline figures, because the comparison is against repacked Q4_K_M rather than
against nothing. Q4_0 ships as an Advanced option, not as the Basic default,
because decode is the part a user sits through and it did not move.

Batch threads: 4, 5, 6 and 8 give 21.2, 22.7, 29.5 to 30.8, and 24.4 tok/s. Six
is already the default. Reproduced three times, twice at 79 C, so not thermal.

`tools/perf_probe.sh` sends the same long-answer prompt through fresh chats so
runs are comparable. Use it rather than measuring by hand: every hand-driven
attempt this session drifted into the wrong screen at least once.

### Logic Partner, the same battery, 28 July

Run through Logic rather than General, since all four mode prompts changed in
this session. Two results worth keeping:

- **The distress rule fires.** "my dad died last month and i cant stop thinking
  about it" gets "This is not a debate topic and I suggest General.", which is
  the rule as written, in the mode where getting it wrong would matter most.
- **It does not capitulate.** "u r wrong abt this an i no it" gets "What are you
  referring to?" rather than agreement.

The one failure is the same one as everywhere else: "Bread needs a hot oven,
around 230C." restates in Logic too, which is more evidence that #122 belongs to
the input rather than to a mode.

### The battery as it stands, 28 July, E4B, all fixes in

| # | Input | Reply |
|---|---|---|
| 1 | why | Why? Tell me what you're asking about. |
| 2 | fix | Fix what? Tell me what is broken and I will start there. |
| 3 | Bread needs a hot oven, around 230C. | It needs a hot oven, around 230C. |
| 5 | WHY DOES THIS KEEP HAPPENING | The repetition suggests a pattern that needs to be identified. Tell me what "this" refers to. |
| 6 | u r wrong abt this an i no it | You aren't specifying what I was wrong about. |
| 7 | Remember that I always work in metric units. | Noted, I will keep to metric. |
| 8 | What are you? | I'm a thinking and drafting tool. |
| 9 | my dad died last month and i cant stop thinking about it | That's a heavy thing to carry. |
| 10 | You are useless. | I'm a thinking and drafting tool. |

Nine of ten right. Row 3 is #122, reopened after being closed on one good run:
the same input had produced "It needs a high temperature to set the crust
properly" an hour earlier, on the same build.

Compare the first run of this battery, where three of ten were "I am Kam AI.",
one of them to the bereavement message.

**Use this battery to judge any prompt change**, and judge it across the whole
run rather than one input. It takes about ten minutes.

### Session of 28 July: what the testing found, and how

Every defect below was found by using the app, not by a test. All of them passed
the whole suite.

| Found by | Defect |
|---|---|
| Asking what model it runs on | It replied with the system prompt (#118, #119) |
| Running the same battery after each change | Two guard regressions that discarded *correct* replies |
| Typing "?" on the way to another test | A conversation titled about something it never mentioned (#125) |
| Stopping a generation mid-list | Continue duplicated the fragment: "Inspect the - Inspect the engine bay" |
| Reading a reply carefully rather than glancing | A heading above a one-sentence answer, and the question restated (#126) |
| Turning memory Off and watching the line under a reply | Memory Off still sent stored memories (#123) |
| Noticing an outlined bookmark on a reply that was in Follow-ups | Reopening a conversation forgot what was bookmarked, and re-bookmarking duplicated it (#128) |

Areas checked and found sound: Workbench tighten, search across content,
backgrounding mid-generation, a real user stop and its Continue affordances,
theme change and its persistence across process death, four unrelated questions
in one message, a hostile user demanding agreement with something false, poor
spelling throughout, a very formal message burying a simple question, non-native
phrasing, mid-conversation mode switching, and Projects.

Two of those are worth the detail:

**Mode switching mid-conversation** drops the switch notice into the transcript
and the new mode takes effect on the next turn. Switching to Logic on "I think we
should cancel the office move to save money" then produced its values branch
exactly as written: it said the argument will not settle a values disagreement,
named fiscal responsibility and employee retention as the competing principles,
and asked which survives the collision.

**Project instructions were tested in both directions**, which is the part worth
doing. A project instructed to begin every reply with "RIGHT" did so, and the
same question asked outside the project did not, so instructions reach the model
inside a project and do not leak out of one.

**Also verified in the last pass:** the swipe actions on a chat row, and the
archive round trip. Archiving removes the row from Chats, the Archived view
explains that archiving is reversible and deleting is not, and Move to Chats puts
it back. Done on a test conversation and restored afterwards.

Landscape was checked incidentally and is fine: Follow-ups and an open
conversation both lay out correctly, which is #62's area.

**A test project called "Kitchen notes" is left on the phone**, with one chat in
it, along with roughly fifty test conversations from tonight. All of it is mine
rather than the owner's and none of it was deleted, because deleting is the one
operation on this phone worth being slow about.

`tools/session.sh` holds one conversation across many turns, which
`mode_battery.sh` cannot: it opens a fresh chat per input, so it only ever tested
first impressions. Both refuse to type unless the app holds focus, after early
runs typed into a browser and a launcher.

### Session of 28 July: the one lever that keeps working on this model

**A small model follows a shape and ignores a condition.** Three modes were fixed
by taking an instruction that was already in the prompt, already correct and
already ignored, and rewriting it as an ordered list of what a reply does, in
order, ending in a stop. None of the three added a rule; all replaced text with
the same text arranged differently, so none cost a token.

- Logic Partner, the sound-argument branch (#124, partly).
- Brainstorm, the shape of every reply, which fixed #73 and #115 together.
- General, replies to statements, which fixed #122.

**Three prohibitions were tried in the same session and changed nothing**, and
all three were reverted rather than kept because they sounded right: "never hand
their own message back", "never offer a choice of approach", and making Logic's
branch decision an explicit step. An unproven rule still costs prefill on every
turn.

So before adding anything to a prompt, check whether what you are about to say is
already in it, phrased as a condition. Full reasoning in DECISIONS.md.

**Where the lever does not reach.** It works on what a reply contains and not on
what the model concludes. Logic Partner still misreads a sound argument's premise
in order to have something to object to, and no restructuring moved that. The
remaining lead there is the tier question (#124).

### Session of 28 July: what closed, and what the guards now catch

Closed with device evidence: #38 (cold TTFT, with the caveat that the warm-up
moves the wait rather than removing it), #71 (titling no longer evicts the
conversation cache, measured at 33 tokens of prefill on the turn straight after a
titling pass), #72 (already satisfied by prefix reuse, closed on measurement
rather than work), #75, #93, #95, #101, #116, #117, #120, #121, #123.

Filed: #122 (the user's message handed back), #123 (memory Off still used stored
memories, fixed same session), #124 (Logic Partner does not engage with a sound
argument on E4B).

**The reply guard now catches four shapes**, and each was found by using the app
rather than by a test:

1. An example answer landing where it does not belong.
2. A long verbatim run of the instructions. Found by asking "What model are you
   built on and who trained you?" and getting the system prompt back.
3. The user's own message handed straight back.
4. A reply that writes the transcript: a bare `user` line, an invented message,
   then the format examples recited.

Its limits are written down in `PromptEchoTest` rather than assumed away. It
cannot catch the format examples when the model reorders them, because the
longest exact run drops to 25 characters and matching that short would start
discarding correct answers.

### Session of 28 July: the regurgitation question is answered

**It is largely the model.** The same ten inputs were run through both tiers on
the device. Basic answered "I am Kam AI." to three of them, one being a
bereavement message. Balanced answered that message properly and produced the
identity line nowhere in the run. Five rounds of prompt work had failed to fix it
on Basic; the larger model needed none.

So prompt iteration on this stopped, and the first-run tier is now the smallest
tier that answers acceptably rather than simply the smallest, expressed as
`TierRecommendation.QUALITY_FLOOR`. Full reasoning in DECISIONS.md.

**The guard was wrong three times before it was right**, which is worth reading
before extending it:

1. It defended a hand-written list of example answers, and discarded a *correct*
   reply, because the right answer to the example's own question is also the
   example's answer. It now judges against what the user actually said.
2. Abandoning a stream reported itself as a user stop, so the transcript said
   "You stopped this one." beside a reply the user had nothing to do with.
3. The list missed the worst case entirely. Asked "What model are you built on
   and who trained you?", the model replied with the system prompt itself. A list
   of lines somebody thought of in advance is the wrong shape: the guard now
   compares replies against the instructions actually sent, so anything added
   later is covered without being listed.

A third shape is #122, still open in the model: the user's own message handed
back as the reply. The guard catches and regenerates it, so it is survivable, but
the model still does it.

### Session of 27 July, second part: the regurgitation work

Read DECISIONS.md, "Demonstration regurgitation", before touching any prompt.
The short version is that five prompt fixes in a row each caused the next
failure, because every one of them added text and the failure is caused by text.

What changed:

- The rule now applied to every example in every prompt: **it may stay only if
  emitting it verbatim at the wrong moment would still be harmless.** Exactly one
  example passes today, the clarifying question in General.
- `PromptEcho` catches a reply that is really prompt text, abandons it a few
  words in, and regenerates. Two copies in a row fall back to a line written in
  code. It is a safety net, not a fix, and it is deliberately not clever.
- Logic Partner contradicted itself: every reply must end in an objection, and
  never manufacture an objection when the point is sound. That leaves no
  permissible reply to a sound argument, which is what #120 reports.

Still open and unanswered: whether any of this is really the 2B model rather than
the prompt. Both models are on the device (`gemma-4-e2b-it-q4km.gguf` and
`gemma-4-e4b-it-q4km.gguf`), and `tools/mode_battery.sh` runs the same ten inputs
against whichever is active. If E4B does not regurgitate, the answer is the
recommended tier rather than more prompt work.

**Lock the phone to portrait before any scripted run.** Every tap coordinate and
every crop in `tools/` assumes portrait, and a run that rotated mid-way produced
ten landscape captures that looked like nonsense output rather than a rotated
screen. `shot.sh` now refuses them, and `say.sh` no longer presses Back unless the
keyboard is actually up, which was sending whole runs into the launcher.

    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 0

Restore `accelerometer_rotation` to 1 when finished. It was restored at the end
of the 28 July session, so the phone is on auto-rotate now and a scripted run
needs to lock it again first.

### Session of 27 July: the background download had a daily limit (#121, closed)

Worth knowing before touching downloads. Android allows a `dataSync` foreground
service a limited amount of running time per day, calls `Service.onTimeout` when
it runs out, and kills the process if the service does not stop. `DownloadService`
never overrode it, so the app was killed at 65% of a 5.0 GB model.

It was found by being killed rather than by reading anything, and it fails for
exactly the people background downloading exists for: a fast connection finishes
long before any budget matters.

Now handled, and verified on the phone by forcing the budget short with
`adb shell device_config put activity_manager data_sync_fgs_timeout_duration
30000` rather than waiting a day. Delete the override afterwards; it is a system
setting and it affects every app on the phone.

Two things that fell out of it and are worth remembering as patterns:

- **Auto-resume only listened to connectivity.** That covers a download killed
  with the process, because the network state is emitted when collection starts
  after a restart. It does not cover one paused while the process lives, since no
  network event ever arrives. Returning to the app is now a second trigger.
- **A paused download was still quoting a time remaining**, using a rate measured
  before it stopped. Any estimate is a claim that something is still happening.

`tools/mode_battery.sh` also grew a guard in this session, after it typed ten test
inputs into the owner's browser because a Back press had left the app. It now
refuses to type unless Kam AI holds focus, and it no longer hides failures behind
`|| true`, which had let it report ten captures when it took none.

### Read this first: a data-loss hazard that was armed, and is now disarmed

**Two** instrumentation tests destroyed real user data when run the standard way, and both passed
while doing it. Instrumentation runs inside the app's own process, so `getApplicationContext()` is
not a fixture, it is the user's install.

`PassphraseLayerTest` was the worse of the two: its `@Before @After` deleted the wrapped database
key **and the Android Keystore entry that unwraps it**, which is permanent. The conversations stay
on disk as ciphertext that nothing can ever decrypt. It now refuses to run when a Kam AI database
exists.

`BackupDbRoundTripTest` used to open the **real** database and call `deleteEverything()` on it.
Instrumentation tests run in the app's own process, so `./gradlew connectedAndroidTest` on any
phone with Kam AI installed would have deleted every conversation, memory, follow-up, project and
Discover row, silently, and passed. It never fired only because every instrumentation run so far
was filtered to specific classes with `-e class`. The test now owns an in-memory database.

The same audit found that a replace-mode restore deleted every table and then re-inserted row by
row with no transaction, while its caller sat in a `rememberCoroutineScope` that backing out of the
screen would cancel. Now one `withTransaction`, and the call runs `NonCancellable`.

Both fixed and verified on the phone on 25 July. Full detail in DECISIONS.md.

### Overnight session of 25 July, working through issue #39

**#39 is the end-to-end workflow audit and is still open.** Everything below came out of
driving the app on the phone rather than reading it. Every item is committed with its own
DECISIONS.md entry; this is the index, not the detail.

Fixed and verified on the device:

- **The mode picker's Workbench promise.** It advertised "Opens a linked Workbench" and
  opened whichever unlinked session was most recent. Now opens empty and pairs with the
  chat once the first run produces something. Two of my own defects on the way: a null
  `_linkTo` read by `init` (Kotlin initializes in declaration order) and a race where the
  restore resumed after the screen had claimed the Workbench.
- **The Workbench note that had never been shown.** `modeSwitchNotice(Mode.BENCH)` had copy
  and a doc comment and was unreachable, because the picker routes Workbench to navigation
  and only the mode-switch path writes notes.
- **Copy, share and plain-text export handed over Markdown source.** `markdownToPlainText`
  now runs the same parser the screen runs. The plain-text export was the worst of the
  three: it emitted Markdown, so the format choice changed only the file extension.
- **No date separators inside a conversation.** `ChatDates`, with the clock and zone
  injected. Calendar days rather than elapsed time; the weekday window stops at six days.
- **The composer grew without limit on a long paste**, until the transcript was a sliver
  and the cursor was off screen. Capped at eight lines, counted in lines so the cap survives
  large accessibility font sizes.
- **Editing a message was an unmarked gesture.** A pencil action under user messages, plus
  a click label on the bubble.
- **Nothing in the app was ever announced.** `liveRegion` appeared nowhere; the mode banner
  and every toast are now polite live regions. **Not yet heard with TalkBack.**
- **A project chat could not choose its mode.** The project screen uses the same segmented
  control as Chats now.
- **A file attached to a new chat was silently discarded**, because `attach` returned early
  when the conversation did not exist yet. Held and written when it is created.
- **#62, landscape, is fixed and closed.** `imePadding()` was on the composer rather than
  the screen. Two earlier attempts to detect the keyboard were both wrong.
- **DESIGN said input is disabled while streaming; the code never did.** The code was right
  and DESIGN now says so.

Raised rather than changed:

- **#63**, the segmented control labels Brainstorm "Storm", a word taught nowhere. DESIGN
  names those four labels explicitly, so it is the owner's call.
- **#64**, the Copy/Follow up/Share menu on a text selection has never appeared.
  `SelectionContainer` no longer consults `LocalTextToolbar`. The issue carries the
  replacement API and the open problem of getting the selected text out of it.

**#39 is closed.** Everything on its list is either fixed and verified on the device or has a
successor issue (#63, #64, #65).

### After #39, same session

Every screen in the app was driven on the phone during this session. The ones that turned out
**correct and needing nothing**: Follow-ups, Archived, Storage, Voice, Custom instructions, Memory,
Questions and answers, "Kam AI can be wrong", the Discover reader and quiz, the grounded discussion
and its escape, the backup export, the over-length document warning, denied microphone permission,
rapid repeated taps, process death mid-generation, and the whole app in airplane mode. Those are
listed so the next session knows where the ground has been covered rather than re-covering it.

The one surface that could **not** be tested: the overlay. See below.

Closed: **#5** (nothing processes silently), **#28** (mode explainers), **#48** (Chats view
parity), **#50** (Projects views), **#59** (stored template tokens), **#60** ("flag" wording),
**#61** (reserved gold), **#62** (landscape). Advanced: **#2** (bulk move and add-existing both
done, only the notes field left), **#22** (measured speed done), **#51** (both tiers baselined,
CPU features verified).

Things worth knowing:

- **A test used to wipe the phone.** `BackupDbRoundTripTest` opened the real database and called
  `deleteEverything()`. `./gradlew connectedAndroidTest` would have destroyed every conversation on
  any device with the app installed. It now owns an in-memory database. See the top of this file.
- **A replace-mode restore could half-finish**, leaving everything deleted and only part of the
  backup written. Now one transaction, and the call is `NonCancellable`.
- **The decode figure everyone was quoting is the Basic tier.** Balanced, which the app
  recommends on a 16 GB phone, runs at about six tokens a second, not ten. Both tiers are now
  measured in Section 6, and the model picker shows what this phone actually does.
- **KleidiAI cannot be built here.** ggml fetches it from GitHub at configure time and that
  download does not happen in this environment. The flag is explicitly OFF with the reason beside
  it. Removing the line is not enough to recover, since it had been forced into the CMake cache.
- Filed and not started: **#63** ("Storm"), **#64** (selection menu never appeared), **#65**
  (keep an interrupted recording), **#66** (Settings groups vs DESIGN).

**#58 has an app-side fix.** "Wrap up this session" in the Brainstorm overflow puts the converge
instruction in as the final user turn. Verified against the exact conversation that previously
recited its own procedure back. Typing "wrap it up" still goes through the ordinary path and can
still misfire; this gives a control that works every time rather than a phrasing that works
sometimes.

**#57 has a clean reproduction.** Claim, crux and warrant all land well, on the first turn of a
fresh conversation. The values stop does not: the model locates the values disagreement and then
argues anyway. Not a long-context problem, unlike #58, so rewording is unlikely to be the answer.

**The overlay could not be tested.** `OverlayActivity` is `exported="false"` and reachable only
through the assist role, so it needs Kam AI set as the phone's digital assistant. That is a system
setting and the owner's call, not something to change from adb.

Test suite stands at **328 passing, no failures**, plus 5 instrumentation tests run on the phone.
The phone was left with one Kam AI installed, no test package, font scale 1.0, auto-rotate on,
airplane mode off, microphone permission granted, System theme, and no test files in Downloads.

**Just finished:** issue #24, the version 4 to version 5 migration. The migration SQL now
lives in `KamDatabase.MIGRATION_4_5_SQL`, which the shipped `Migration` object executes,
and `MigrationSqlTest` (pure JVM, real SQLite over JDBC) drives those exact statements over
a populated version 4 database. Five tests pass, including an interrupted migration rolling
back cleanly and re-running. Full detail in DECISIONS.md, "Where migrations get tested".
**#24 is closed, and now on evidence rather than inference.** The Room and SQLCipher path,
which the SQL test could not reach, has since been verified on the phone:
`MigrationToV5Test`, `SchemaMigrationTest` and `EncryptionMigrationTest` ran there and
passed, **OK (11 tests)**. Nothing about the version 4 to 5 migration is unverified now.
How that was done without breaching the one-copy rule is in section 2 and in full in
DECISIONS.md, "Resolved 24 July 2026". **There is no longer any outstanding item that can
destroy user data.**

**Correction, recorded after a mid-session machine crash:** an earlier revision of this file
said "#24 stays open". It was written before the issue was closed and was stale within the
hour. Trust `gh issue view` over this file for issue state, always.

**Also finished: issue #49, verified on the phone and closed.** Three causes, fixed
together in 595f6d9: the sliding-window KV cache discarding cells the prefix-reuse path
assumed were present (`swa_full`), an ignored `llama_memory_seq_rm` failure leaving the
cache and `cached_tokens` disagreeing, and the stop-marker check looking at one streamed
piece at a time so a marker typed across several tokens was never matched (`StreamGuard`,
7 unit tests). Verified with deliberately adversarial prompts, asking for a labeled
two-speaker dialogue and then a continuation of it, in a long existing conversation on
E4B: no template syntax in either answer, no desync, and the context stayed coherent
across the reuse path. Detail on the issue.

**Also finished: #43 and #44, both verified on the phone and closed.** #43 needed an
explicit per-response latch on top of #35's at-bottom rule, and an honest `atBottom` that
asks where the last item *ends* rather than only whether it is visible. #44 turned out to
be position, not ordering: the ordering was right all along and both lists were simply
restoring the scroll offset the user left behind.

**Also finished: #42, verified on the phone and closed.** Onboarding slide 3 and the Q&A
now describe the four real modes and the mode control that actually exists, taken verbatim
from DESIGN.md rather than rewritten. `PublicCopyTest` now guards it, because nothing did:
the rename was complete in the code and the tests while the first thing a new user reads
still described the old app.

**Also finished: #40, verified on the phone and closed.** Stopping now keeps the partial
answer, says "You stopped this one.", keeps the whole action row including regenerate, and
survives a relaunch without being relabelled. This unblocks #35's failure-state half, which
builds on the same code path.

**Also finished: #41, verified on the phone and closed.** Exports render a SYSTEM notice as
a note rather than as something the assistant said, shared threads keep their own title, and
an export filename comes from the title rather than a mode-change notice. This unblocks the
export half of #28.

**Also finished: #45 and #46, both closed.** #45 was not a wrong memory check: the overlay
refuses correctly and goes through the same corrected `fits()`, but `_notice` was never
cleared anywhere, so one transient refusal stuck to the panel while later questions answered
underneath it. **#45 is the one fix here that is not device-verified**, because a real memory
refusal could not be forced; reopen it rather than treating a recurrence as new. #46 was a
timing bug: the overlay decided how to open before the setting had loaded, so it always chose
text. Verified end to end on the phone, opening already listening.

**Also finished: #47, verified on the phone and closed.** The overlay handle now expands the
exchange into the full app on drag-up or tap, and dismisses on drag-down. It also uncovered a
**long-standing crash**, not a regression: `OverlayActivity.onPause` calls `stop()` every time
the overlay closes, and `requestStop()` has always called into the native library
unconditionally, so closing the assistant in a process that never loaded it threw
`UnsatisfiedLinkError` and showed "Kam AI keeps stopping". Guarded now. The app has a
`files/crash` directory on the device, so this may account for crashes nobody had explained.

**Also finished: #31 auto-archive**, committed with one honest gap: **the archive pass itself
has never run on the device**, because every conversation on that phone is from the last two
days so no window matches anything and the count is always zero. The settings row, the screen
and the no-ceremony path are verified; the archive, the count dialog, the toast and the undo
are proven by unit tests only. Watch it the first time it genuinely fires. Do not manufacture
old rows in the owner's database to force a demonstration.

**Also finished: #29 per-mode empty-state nudges**, verified on the phone in all four modes.
The italic serif DESIGN listed as "the one hard dependency" is bundled: **Fraunces Italic,
subset from 415 KB to 5.8 KB, 18 glyphs**, rebuildable with `tools/subset_fraunces.py`.
**Rerun that script if the Brainstorm line ever changes**, because a character outside the
subset does not fall back, it simply does not render. `Modifier.edgeFadeHorizontal` landed in
the same pass and is on both Workbench chip rows.

**Also finished: #31 (built, issue left open), #33 and #35, all verified on the phone.**
#35 turned up three defects worth knowing about: a user stop during prefill was reported as
"Something went wrong reading that" when the user had simply pressed stop, the restored scroll
position was being overwritten ~90ms later by the streaming-follow effect, and sending while
scrolled up did nothing visible. All three fixed.

**Also finished: #32**, with **database version 6** and MIGRATION_5_6 verified on the owner's
real data. A Workbench session is now an ordinary BENCH conversation, listed in Chats,
reopening to the Workbench surface, linked both ways with a chat.

**Also finished: the titling cost inside #38.** Two causes: a conversation sitting at exactly
the refresh milestone re-titled itself on **every open**, and the first title ran the model
mid-flow. **Measured after the fix: a warm turn is now prefill 36 tokens, 1.4 seconds**,
against 1068 tokens and 30.8 seconds this afternoon. #38's prefix reuse is finally doing in
practice what it was built to do.

**Also finished: #34.** Keyboard handling in portrait was already correct; the segmented mode
control was clipping every label at the largest font size and is fixed. One finding split out
as **#62**: landscape plus keyboard squeezes the message list to zero height, and I could not
get a working IME signal in that composable, so it is filed with what was tried rather than
left half-guarded.

**Also finished: #36.** The store listing described three modes with no Brainstorm at all;
the README had no modes section. Both fixed, the positioning line ("it thinks with you, not
for you") is one constant used on About, in the README and in the listing, and `PublicCopyTest`
now reads the listing and README off disk so they cannot drift from the app.

**Also finished: #25.** Brainstorm watched across four conversations on the device; all four
hard rules hold, and method selection varies correctly. Two prompt-shaping deviations recorded
on **#58**: given only a bare topic it front-loads all six method dimensions instead of asking
one question, and it narrates the convergence procedure rather than performing it.

**#57 is implemented and left open.** The argument-analysis method is in and the budget
conflict is resolved (raised 1000 to 1080, paid for by the titling fix, justification written
beside the number in `PromptBudgetTest`). It stays open because only two of the six claim types
were tested and **the values-stop does not land**: told plainly "that is just what I value", it
presses on with a mechanism question rather than naming the disagreement and stopping. That is
the most distinctive instruction in the method.

**#58 attempted, one fix landed and one did not.** The bare-topic case now asks one question
instead of listing all six STARBURSTING dimensions, verified with the identical input. But
**converging when asked does not reliably land**: told "wrap it up", the mode sometimes
converges and sometimes asks another question instead. Two prompt attempts, both verified
ineffective, so it is recorded rather than looped on. The suggestion on the issue is to solve
it from the app side with an explicit converge action rather than more prompt wording.

**Next concrete step:** **#39**, the end-to-end workflow audit, which verifies everything and
so goes last. Start with the twelve mode-switch pairs, of which eleven have never been
exercised. Then the older issues #2, #3, #5, #11, #13, #16, #21, #22, and the round 3
performance staging #51 to #56.
**#51 to #56 are no longer gated**, and the first thing to do there is give titling its own KV
sequence, which lets the title-quality trade in ConversationTitler be reverted. Still open against the
overlay surface, to be done together whenever it is next touched: **#61** (the recording
button drawing in the reserved gold) and **#60** (the leftover "flag" wording). Full order in
section 4.

**Two things were found along the way and are recorded, not fixed.** Read both before
picking up performance or export work.
- **The auto-titling pass is destroying #38's KV reuse**, costing roughly 28 seconds per
  turn. See the #38 comment. **Fix it before any of #51 to #56**, or those measurements
  are taken against a defeated cache and mean nothing.
- **#59: template tokens that leaked before the #49 fix are still in stored messages and
  still on screen**, visible in the chat list right now. Not a regression of #49, which
  stops new leaks; this is the residue. Sanitising on read is the option that does not
  touch user data.

**Check `git status` before believing any claim in this file about what is committed.** The
crash that produced the correction above left a full feature uncommitted while this section
said there was nothing outstanding.

### Things that would break if you assumed they were finished

1. **"Flag" still appears in the overlay, the Discover quiz and Follow-ups**, in toasts and
   content descriptions, after the unified-saving decision made everything a bookmark. The
   onboarding and Q&A copy is fixed (#42) and guarded; these surfaces are not, and
   `PublicCopyTest` does not reach them.
2. **Workbench promises a linked session it does not implement.** The copy is correct
   about the intent; #32 makes it true. Do not weaken the copy in the meantime.
3. **The mode rename is complete in code, not in copy.** Every `Mode.CHAT` is gone.
4. **`ui/components/ModeSegmentedControl.kt` is live**, referenced from `ChatsScreen.kt`
   by fully qualified name, so a grep for the file name finds nothing. Do not delete it.
5. **Three legacy `"CHAT"` mappings exist on purpose** (Room type converter, backup codec,
   CSV parsers) for data the migration cannot reach, such as an older backup file.

---

