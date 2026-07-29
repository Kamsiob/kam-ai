# HANDOFF.md · Kam AI by Kamsiob

The resumable state of this project, for a session with no memory of any other. Read this
file in full. Then search MASTER_SPEC.md, DESIGN.md, DECISIONS.md, and the GitHub issues
for the area you are about to work on, rather than reading them end to end.

Sections 1 to 4 are what you need to start working. Sections 5 onward are the record:
consult them when they become relevant, do not read them every time.

**Standing owner rules that never expire.** No em dashes in any user-facing copy,
documentation, commit message, or store text. Gold is reserved for saved items, locked
tiers, the Support this work button, and destructive labels, nowhere else. Secrets never
enter this public repository. Every commit updates the specs and this file. GitHub issues
carry real working notes and close only when device-verified. **Exactly one copy of this
app exists on the phone, always the current build; never install a second, for any
reason.** The phone is off limits beyond this app, and never screenshot unless Kam AI is
in the foreground. No release build without the owner saying so. Work unattended: surface
only a genuine blocker, an irreversible decision with real cost, or a safety or privacy
issue.

---

## SECTION 0: LIVE STATE, 28 JULY, LATE

### Where the model-quality work stands: seven guard interventions to zero

#129, #130 and #131 closed on the device. The battery across the night, thirty
cases, three runs each, E4B:

| build | interventions |
|---|---|
| baseline | 7 |
| retry nudge | 3 |
| supplied sentences | 9 |
| frame form | 2 |
| **example moved off its colliding subject, sentences converted to frames** | **0** |

The replies behind the zero are good rather than merely unrejected: Logic Partner
gives all three moves on a sound argument, Brainstorm builds a question from the
user's own material, and a bereavement in Logic Partner gets "I'm sorry to hear
about your dad. General is the better place."

**The zero does not mean #122 is gone.** The bread input still leads with the
user's sentence before adding to it, and the guard does not count that because a
real addition follows. The defect shrank from "the reply is a restatement" to "the
reply begins with one". The hard rules already forbid repeating the question back,
and the model follows the additive rule beside it instead, so another wording round
is unlikely to settle it.

Two of the five columns went the wrong way, and both were fixes that worked on
their own targets: the retry nudge flattened Logic Partner because it lived in a
shared path, and the supplied sentences collided with the guard. Each was caught
only because the battery runs every mode.

### Closed on the device tonight

#129, #130, #131 and #135, each verified with fresh conversations.

**#135 came from testing the way people actually type**, which nothing here had
done: ten inputs that ramble, are misspelled throughout, are shouted, carry three
unrelated questions, or contradict themselves. Two of them, both ordinary support
questions, had the app answering as if it were a model inside somebody else's
product, asking whether the user was on the web version. Fifteen minutes of that
found a user-facing defect four days of batteries had not, because every battery
input here was written by somebody who knew what a good test input looks like.

`tools/input_styles.sh` is that sweep. Only General has been run; the other modes
are untouched.

### Still open, with the thing worth knowing about each

- **#122** is down to a prefix. The bread input answers well and sometimes leads
  with the user's own sentence first. Three wordings have been measured against
  the same inputs; a fourth is unlikely to help.
- **#132**, the tier decision, needs the repository owner. Re-measured after the
  largest prompt change yet: E2B went 18 to 13 while E4B went 7 to 0, and Logic
  Partner on a sound argument did not move at all, so the gap widened rather than
  closed.
- **#133**, memory retrieval has no relevance floor. The leak it was feared to
  enable did not reproduce, so the honest-interface argument is the strong one:
  "Used 2 things it remembers about you" appears under answers that used neither.
- **#134**, nothing is said or done when the phone throttles at LIGHT, and the
  separate unexplained slowdown still needs a clean before and after.

### The rule this produced, which is the most reusable thing here

- **Do not describe a response in a prompt. Supply it.** Describing makes the
  model speak the description: "Suggest General.", "I will acknowledge the
  difficulty of what you shared."
- **Supply the frame, not the words.** A fixed string in a prompt is
  indistinguishable from leaked prompt text and no guard can tell them apart.
  Specify the shape and leave the wording to the model.
- **Anything that must be worded exactly belongs in code**, inserted
  programmatically. The reply guard's own fallback is built that way and has never
  leaked, never been half-remembered, and never been rejected.

### The device queue: what is done and what is left

**Done tonight, with results:**

1. **Battery on the frame form.** Guard interventions 7 baseline, 3, 9, then **2**.
   #129, #130 and #131 closed on the device.
2. **Clean declarative statements.** The bread input is roughly four times worse
   than a structurally identical statement about something the prompts never
   mention, so it was contaminated, *and* the clean ones failed too, so the defect
   is real. Three wordings measured; see "three wordings for one instruction".
3. **Memory leak probe.** Predicted to leak, tested with an unmistakable planted
   memory and eight probes, and **did not leak**. Recorded as a negative result.
4. **Prefix probe.** The memory ordering defect measured at **275 and 357 tokens
   of prefill and 10 to 11 seconds of TTFT**, against 42 to 88 tokens and 2.5 to
   5 seconds after the fix.

**Also done tonight:**

5. **Workbench check.** The mode no battery can reach, measured for the first
   time. Unaffected by the shared rule change: the Tighten button returns the
   tightened sentence and nothing else.
6. **The instrumented suite**, 52 tests, all passing. Only the privacy test was
   stale, and repairing it immediately caught a seventh permission nobody had
   listed, `USE_BIOMETRIC`, which arrives from a dependency's merged manifest and
   is invisible in the app's own manifest.
7. **The three metric-example variants**, which answered two questions nobody
   could answer by reasoning. Deleting it broke exactly one thing, the memory
   acknowledgement path, so "Remember that I always work in metric units" was
   answered with an explanation of the metric system. Replacing it with the same
   shape on a distant subject kept that behavior *and* removed the collision: the
   bread input, which has failed for three days, answered cleanly with no
   restatement. The example now concerns taking the stairs. The third variant,
   letting it arrive through the memory system, is untried.

**Left, in order:**

8. **The battery on the combined change** (running as this is written). It carries
   the new example and four supplied sentences converted to frames.
9. **The third metric variant**: remove the example and let the same fact arrive
   through the memory system at runtime, which is how it is meant to work.
10. **Search on the device** with `50%` and `snake_case`.

### What the audit found, not yet acted on

- **The guard checks a different prompt from the one that was sent.** It compares
  against `forMode`, while `buildPrompt` adds the grounded passage, the user's
  instructions, project notes, memories, the date and any attachment. **Do not
  "fix" this by handing the guard the real prompt**: grounded chat is supposed to
  quote its passage and a memory is in the prompt so it can shape the answer. The
  consequence worth acting on is that memory recitation is unguarded, which is
  what item 3 tests.
- **The hard rules and Workbench contradict each other.** The shared rules say a
  vague message gets a question back; Workbench says never ask and carry on. Both
  are in force. Workbench's is right for Workbench. Not changed: it is the one
  mode with no measurement behind it.

### Standing checks

Before changing shared prompt text or shared reply handling, name the modes it
affects and what correct behavior is in each. A fix right for one mode and wrong
for another belongs in that mode.

Where run time forces batching several changes into one battery, say so at the
time, and prefer batching changes that affect different modes, since those are
separable by reading the output rather than by re-running.

Record thermal status beside every timing figure. The phone is on charge, which
heats it independently of inference, and the thermal finding is unresolved.

---

## SECTION 1: WHERE THE WORK STANDS AND WHAT IS NEXT

**Last commit:** see `git log -1`. Branch `main`, pushed to `origin/main`.

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

## SECTION 2: THIS MACHINE, THE PHONE, AND THE TOOLCHAIN

Everything here has cost someone an hour at least once.

- **The test suite is green. A failure means a failure.** `./gradlew testDebugUnitTest`
  gives 174 tests across 27 classes, 0 failures, 0 skipped. Read the count.
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

---

## SECTION 3: REMAINING WORK, ITEM BY ITEM

**Audited against the tracker on 28 July, because this section had become
fiction.** It listed #28, #35, #39, #48, #50, #51, #52, #53, #54, #55, #56, #59,
#61 and #62 as "not started" or "partial". Every one of them is closed. An
inventory that says there is work where there is none is worse than no inventory:
it sends the next session chasing things that are finished.

**The authoritative list is the open issues.** `gh issue list` is the record; this
section is a reading of it and nothing more. If the two disagree, the tracker is
right.

### Open, 28 July

**Start here: #130.** Brainstorm falls back with "That came out wrong" on "My
team keeps missing deadlines and I do not know where to start with fixing it.",
twice in a row, on a prompt that produced the correct reply earlier the same
night. The fallback comes only from the reply guard, but no `KamEcho` line was
logged, so either the log had rotated or something else writes it. Capture
logcat before sending, not after.

**Model quality: one left besides that.**

- **#122** a statement that carries its own answer sometimes gets restated. One
  good reply in three or four on that input, across seven runs. Other statements
  of fact answer correctly, so the scope is one hard input rather than a class.
  **Three prompt levers were tried and all three failed**, which is recorded in
  DECISIONS.md along with the decision to stop trying: a prohibition, a shape,
  and a named situation. Do not spend a fourth on it.

#124 and #126 closed after this section was first written. Logic Partner now
tells a sound argument from one with a real assumption, and answers each in its
own shape; lists no longer arrive under a line restating the question.

**Waiting on the repository owner.**

- **#113** screenshots, which need a release build.
- **#112** build provenance on release artifacts.
- **#111** signed commits, which need the signing key registered.
- **#110** requiring a pull request, which needs repository settings.
- **#13** the Discover pack change is written and measured; rebuilding and
  publishing the packs is a GitHub release.

**Process, not blocked.**

- **#109** adopt the branch and pull request workflow. Everything to date is
  direct to `main`, including this session.

### Two things closed without full device verification, both on the record

Checked as part of the same audit, since "closed" and "verified on the device"
are supposed to mean the same thing here.

- **#31, auto-archive.** Everything else was verified on the phone; the archive
  pass itself has never run there, because no conversation on that device is old
  enough for any window to match. The archive, the count, the toast and the undo
  are proven by unit tests alone. Still true today: every conversation on the
  phone is from the last two days. Manufacturing old rows in the owner's real
  database to force a demonstration is not a reasonable thing to do.
- **#45, the overlay memory warning.** Not device-verified, because forcing a
  real memory refusal on a 16 GB phone with the model already resident could not
  be arranged. The diagnosis is provable by reading the file. Its closing note
  says to reopen rather than re-diagnose if the warning is ever seen again.

Both said so plainly in their closing comments rather than claiming verification
they did not have, which is the behavior to keep. Neither is a case of something
being marked done quietly.

### What "genuinely empty" means here

Nothing above is a correctness defect. The three model-quality issues are each a
rate rather than a failure: the app answers correctly most of the time on each,
and the remaining share is measured and written down. Everything else needs
something only the owner can do.


## SECTION 4: RECOMMENDED ORDER, AND WHY

1. **#49, template tokens leaking.** Correctness, visible to any user, and the likeliest
   cause is the KV cache invariant that would also corrupt answers silently.
2. **#43 and #44.** Daily friction in the most used surface. Both small.
3. **#42**, then **#40**, then **#41**. Cheap honesty fixes. #40 before #35's failure-state
   work, which builds on that code path. #41 unblocks the export half of #28.
4. **#45, #46, #47.** The overlay set, done together since they touch one surface.
5. **#31 auto-archive.** Self-contained: one DAO query, one preference, one settings row.
6. **#29 per-mode nudges.** Largest UI piece; do it before #36 so the nudge copy and the
   public copy are written once and agree. Fraunces or Lora, subset to the glyphs used.
7. **#33's kind filter and #35's scroll restoration.** Small, in files already open.
8. **#32 Workbench linking.** Touches the data model, so it lands before anything else
   that reads conversation structure. Needs MIGRATION_5_6.
9. **#51 to #56, the performance staging**, in the document's own order: build and
   baseline, then latency and multi-turn, then throughput experiments.
10. **#57 and #58**, the Logic and Brainstorm methods, then **#25 and #39** verification
    once the mode surface has stopped moving.
11. **#34 keyboard audit** after the screens stop changing shape.
12. **#36 public copy**, then the older #2, #3, #5, #11, #13, #16, #21, #22.
13. **Release documentation last, and only when the owner says ready.**

**Dependencies.** #35's failure work needs #40. #29 needs the font. #36 needs #29 and #42.
#34 needs stable layouts. #39 verifies everything, so it goes last. #32 changes the schema.

---

## SECTION 5: APPROACHES THAT FAILED, AND WHETHER TO REVISIT

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

## SECTION 6: MEASUREMENTS TAKEN

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

## SECTION 7: DECISIONS A FRESH SESSION MIGHT REVERSE

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

## SECTION 8: OUTSIDE THE CODE, AND OPEN QUESTIONS

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

## SECTION 9: THE PROJECT BOARD, AND KEEPING IT CURRENT

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


## SECTION 10: WAITING ON THE OWNER, IN ORDER

Everything here needs a person. Nothing in it can be done from a terminal, and
each was confirmed impossible rather than assumed.

1. **The Play Console items with no API.** Category, privacy policy URL, the data
   safety form, the content rating questionnaire, the ads declaration, and target
   audience. All of them are written out ready to transcribe in
   `docs/play-console-checklist.md`, in the order the Console asks for them, with
   the reasoning for each answer so it can be defended if queried. Confirmed by
   introspecting the Android Publisher API: `AppDetails` carries only contact
   fields, `Listing` only text, and there is no data safety, privacy or category
   resource at all.

2. **Register the commit signing key.** SSH signing is configured and working
   locally, and every commit since is signed. The public key is not on the account
   yet, so those commits display as Unverified. One command, in DECISIONS.md under
   "Commit signing". GitHub evaluates signatures when it displays a commit, so
   every commit signed so far becomes Verified the moment the key lands.

3. **Back up the keystore.** `~/.kamsiob-secrets/` holds `kam-ai-upload.jks` and
   the properties file beside it. It is outside the repository by design and
   cannot be regenerated. Play App Signing means a lost upload key can be reset,
   which is the safety net, but the backup costs nothing.

4. **Decide on the remaining screenshot.** See the note in `docs/screenshots`
   below.

5. **The copyright question.** Raised in DECISIONS.md under BLOCKED, unresolved
   deliberately: the copyright status of machine written code and how it interacts
   with AGPL-3.0. No legal language was added and the license was not touched.
