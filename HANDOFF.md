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

## SECTION 1: WHERE THE WORK STANDS AND WHAT IS NEXT

**Last commit:** see `git log -1`. Branch `main`, pushed to `origin/main`.

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
7 unit tests). Verified with deliberately adversarial prompts, asking for a labelled
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

**Next concrete step:** **#40** (stopping a response loses its reason, hides the action row
and gets mislabelled on the next launch), then **#41** (export attribution, which unblocks
the export half of #28). #40 comes before #35's failure-state work, which builds on that
code path. Full order in section 4.

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
2. **Stopping a response loses its stop reason** and hides the whole action row, then gets
   mislabelled on the next launch. Issue #40.
3. **Exports attribute mode-change notices to the assistant.** Issue #41.
4. **Workbench promises a linked session it does not implement.** The copy is correct
   about the intent; #32 makes it true. Do not weaken the copy in the meantime.
5. **The mode rename is complete in code, not in copy.** Every `Mode.CHAT` is gone.
6. **`ui/components/ModeSegmentedControl.kt` is live**, referenced from `ChatsScreen.kt`
   by fully qualified name, so a grep for the file name finds nothing. Do not delete it.
7. **Three legacy `"CHAT"` mappings exist on purpose** (Room type converter, backup codec,
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
  `targetSdk` 36 (AndroidX requires it; targeting 37 would opt into untested behaviour).
- **llama.cpp b10058 and whisper.cpp are vendored but not committed**, fetched by
  `tools/fetch_llama.sh` and `tools/fetch_whisper.sh`. `git describe` inside those trees
  returns this app's commit, which is confusing the first time.
- **The debug APK's native code is not an unoptimised build.** Release, `-O3`,
  `-march=armv8.2-a+dotprod+i8mm+fp16`, repacking on, mmap on, flash-attn AUTO, batch 512.
  Debug versus release is not a performance variable. Do not chase it.

---

## SECTION 3: REMAINING WORK, ITEM BY ITEM

Status vocabulary: **verified** (done, seen working on the phone), **unverified** (written,
never watched on the device), **partial**, **not started**, **blocked**.

### Correctness defects, all not started

| # | Item | State |
|---|---|---|
| #49 | Chat template tokens leak into responses in longer conversations | **closed, verified on the phone** with adversarial labelled-dialogue prompts |
| #40 | Stopping a response loses its reason, hides the action row, then gets mislabelled | not started |
| #41 | Exports attribute mode-change notices to the assistant; shared threads lose their title; export filename can come from a SYSTEM notice | not started |
| #42 | Onboarding slide 3 and the "What are the modes?" Q&A describe three modes and a dead switcher | **closed, verified on the phone.** Guarded by PublicCopyTest |
| #45 | Overlay shows a memory warning and then works anyway | not started |
| #59 | Template tokens leaked before the #49 fix are still stored and still displayed | not started. Sanitise on read; do not rewrite user rows |
| #46 | Assistant voice-first setting has no effect | not started |

### Daily-use friction from live testing, all not started

| # | Item | State |
|---|---|---|
| #43 | Scrolling is fought during a long streaming response; needs a per-response latch | **closed, verified on the phone.** Per-response latch plus an offset-based atBottom, in ui/chat/ScrollFollow.kt, 13 tests |
| #44 | A new conversation is not at the top of Chats, and the list does not return to the top | **closed, verified on the phone** in list and grid. Ordering was already right; the lists were restoring their old scroll offset |
| #47 | The overlay drag handle is decorative; make it expand into the full app | not started |
| #48 | Archived conversations unreachable in grid view; audit all three views against each other | not started |
| #50 | Projects screen has no view options | not started |

### Four Mode Update, remaining

| # | Item | State |
|---|---|---|
| #24 | Version 4 to 5 migration | **closed and fully verified.** SQL by MigrationSqlTest on the JVM, Room and SQLCipher by the three androidTest classes run on the phone, 11 tests passing |
| #25 | Brainstorm behaviour on the device: ten methods, selection checklist, four hard rules | prompt done, **behaviour never watched**. Budget a full session |
| #28 | First-time per-mode explainers (needs a "seen once" key that does not exist anywhere yet), per-mode Q&A entries, export markers (#41) | partial |
| #29 | Per-mode empty-state nudges: wash, four hand-drawn double-stroke sketches, per-mode type | not started, largest UI piece. Serif now decided: **Fraunces**, or Lora if Fraunces is awkward at that size, both SIL OFL. **Subset it to only the glyphs the one line needs** and record the file size in DECISIONS.md. Horizontal `edgeFade` variant belongs to the same pass (three chip rows need it) |
| #31 | Auto-archive: Off / 3 / 7 / 30 days, pinned exempt, count before confirming, undo | not started. No time-based or bulk query, no preference key, no settings row exist |
| #32 | Workbench linking, both directions | not started. **Touches the data model, needs MIGRATION_5_6.** No Workbench entity exists; it persists two strings through the settings table |
| #33 | Filter follow-ups by kind alongside source | not started. Brainstorm-defaults-to-pursue path also unverified |
| #34 | Keyboard and reachability audit | not started. Nothing in the app reacts to the keyboard opening and the message list has no IME padding. Do after #29 |
| #35 | Per-conversation scroll restoration; honest incomplete state with retry, continue, discard | partial. Jump-to-latest and non-yanking scroll landed but **only ever seen in their hidden state**. The failure-state half is blocked behind #40 |
| #36 | Onboarding and public copy for four modes | not started. Do after #29 and #42 |
| #38 | Titling KV pollution (**now measured, and severe**), Bench/Overlay/Discover prompt trims, runtime network monitor | partial. Titling costs ~28s per turn by destroying the prefix reuse, and runs after every turn rather than once. Numbers in the issue comment. Fix it before any round 3 perf work, or every measurement taken there is against a defeated cache |
| #39 | Usability gaps and end-to-end workflows, **including eleven of the twelve mode-switch pairs never exercised** | not started |

### Performance, round 3

| # | Item | State |
|---|---|---|
| #51 | Stage 1: KleidiAI microkernels, confirm repacking and dotprod actually engage at runtime, core affinity, cheap link flags, re-baseline per tier | not started |
| #52 | Stage 2: KV state persistence across sessions, flat time to first token at turns 1, 5, 20, invalidation cases, cross-conversation prefix reuse, titling cost | not started. **Privacy constraint: state files must not be plaintext** |
| #53 | Flash attention set deliberately, q8_0 KV cache, tested at real context lengths | not started |
| #54 | Speculative decoding with the Gemma drafter, server-style context setup; draftless n-gram mode | not started, un-deferred by round 3 |
| #55 | Q4_0 repacking versus Q4_K_M versus Q5_K_M per tier | not started, measure before proposing any catalogue change |
| #56 | Physical batch sweep, warmup at launch, mmap versus locked pages | not started |

### Mode method work, round 3

| # | Item | State |
|---|---|---|
| #57 | Logic Partner: analyse before attacking (claim, grounds, warrant, qualifier, claim type), find the crux, challenge well | not started. **Conflicts with the token budget, see DECISIONS.md** |
| #58 | Brainstorm: ground the method in facilitation practice, then verify with #25 | not started |

### Older worklist items still open (issues #1 to #22)

- **#2 Projects.** Remaining: multi-select bulk move from the chat list, add-existing from
  inside a project, optional project notes field. **Its title still mentions Today, which
  is cancelled; fix the title and body.**
- **#3 Inference speed.** Superseded on measurement by #38 and round 3; keep only for
  per-tier tok/s across E4B and 12B.
- **#5 Nothing processes silently.** Remaining: the app-wide audit. #40 is an instance of
  exactly this failure found after the fact.
- **#11** scoped slide-up surface for Discover discussion. Not built.
- **#13** Discover packs carrying full articles. Needs the pipeline and release change.
- **#16** memory contradiction supersession, and a "memory influenced this" indicator.
- **#21** broader audit for other invisible walls.
- **#22** measured speed and quality ratings, input-bar gating, three-state controls,
  attachment filtering, honest model switching with existing attachments.
- Not yet an issue: **conversation view models are Activity-scoped and not cleared on
  back-pop**, so each opened chat leaks a lightweight ChatViewModel for the session.
  Correctness is fine. Worth opening an issue so it stops living in a markdown note.

### Written but never verified on the device

- ~~Jump-to-latest in its **visible** state~~ **seen working 24 July 2026**: after a reply
  arrived in a long conversation the list did not yank, the control appeared, and tapping
  it moved to the latest message. The non-yanking behaviour was seen at the same time. What
  is still unverified is scroll **restoration** when reopening a conversation, and #43
  changes the rule for streaming anyway.
- Eleven of twelve mode-switch pairs (#39). Only General to Logic has been exercised.
- The Brainstorm prompt's actual behaviour (#25).
- Follow-up kind auto-assignment from a Brainstorm conversation (#33).
- Whether the trimmed Logic and Brainstorm prompts still behave the same. Tokens came out;
  nobody watched the model use them.
- The prefill thread-count change in isolation.
- Whether the mode reaches the model when a conversation is opened from search, from a
  follow-up, from a project, or from the share-sheet intake.

---

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
- *Coloured bars, borders, tints, or text tags for mode identity on chat rows.* All made a
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

**Caveat added 24 July 2026, and it matters: the warm-turn figure only holds when nothing
titles in between.** Measured on E4B, turn 2 of a real conversation re-prefilled all 1068
tokens in 30.8s, because the auto-titling pass ran after turn 1 with a different prompt and
overwrote the cache. It should have been about 110 new tokens and about 3s. Titling runs
after **every** turn, not once as earlier notes claimed. See the #38 comment.

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

Colour contrast, measured: `#8A5F0D` 5.12 on ivory and 5.64 on white; `#EFA913` 1.84 on
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
- **The mode picker does not switch on the spot.** Switching changes behaviour mid
  conversation and should be deliberate.
- **Mode colours are identity only,** never UI state. A mode colour on a button is a bug.
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
- **The Today tab is cancelled outright, not deferred.** Its spec was deleted and it is on
  the Not planned list. Do not resurrect it because an older document mentions it.
- **Gemma 4 across every tier:** one family, one licence, one prompt format. No Qwen.
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
2. Did the prompt trim cost behavioural quality in Logic and Brainstorm? Tokens came out;
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
