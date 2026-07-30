# Coverage audit

What has actually been exercised, feature by feature, and what has not.

**Why this exists.** All 18 open issues came from examining modes, prompts, guards, memory,
thermals or instruments. That is where the attention has been and it has been productive.
It is also narrow. Whole features have no issues, and **the reason is that nobody has looked
at them rather than that they are clean.**

**Unit coverage is not evidence here, and there is proof.** A passing suite of 662 tests
across 86 classes did not catch a crash reachable by a key event (#146), a false privacy
claim sitting on the guard's exemption list, or a guard bypass releasable by typing the word
"remember". Those were found by driving the device, by sweeping for a pattern, and by a
checker. So "covered by unit tests" is recorded here as its own category and is never
counted as exercised.

## The three states

| state | meaning |
|---|---|
| **device** | driven on the phone, outcome observed, not merely built |
| **unit** | pure logic covered by the JVM suite, never observed running |
| **none** | neither, and therefore unknown rather than working |

Where something is partly covered, the split is stated. A feature is only **device** when the
user-visible outcome was observed, not when a screen was reached.

---

## Exercised on the device

| area | what was observed | confidence |
|---|---|---|
| Chat, main surface | thousands of messages across batteries, probes, walks | high |
| Modes, four chat modes | battery per mode, mode chips, mode switching | high |
| Memory retrieval and the note | #133 floor and #144 toggle, verified per case | high |
| Prompt guards, main chat only | echo, restatement, prompt text, over many runs | main chat only |
| Thermals, at idle | measured, and the measurement was void | see #134 |
| Navigation and the screen stack | 33 walk steps, plus #146's double-activation | high |
| Settings rows and screens | walk steps 17 to 22, plus font scale 2.0 | high |
| Model download and tier choice | #75, #119, #121, on device | high |
| Crash-free walk | 22 + 11 steps, on the repaired instrument | high |
| Discover, the card | dealt, read, pack count, moment text | card only |
| Accessibility semantics, toggles | `MemoryNoteToggleSemanticsTest` on device | toggles only |

## Never exercised: the list to work through

Ordered by what a first-day user is most likely to touch.

### Projects — **none**

No issue, no probe, no walk step. Everything below is unknown.

- [ ] Creating a project
- [ ] Project instructions, and whether they reach the prompt
- [ ] Moving a conversation in, out, and between projects
- [ ] **Instructions applying forward from the point of a move**, not retroactively
- [ ] Deleting a project, and what becomes of its conversations
- [ ] A conversation started inside a project carrying both its project and its mode

The move case is the one most likely to be wrong, because it is the only one where two pieces
of state have to agree about a point in time.

### Follow-ups — **none**, and it had a defect before

- [ ] Saving from a conversation, from Discover, and from the overlay
- [ ] The check and pursue kinds
- [ ] The source filter
- [ ] Selecting specific text to bookmark
- [ ] Completion state
- [ ] **Whether bookmark state survives reopening**, which was a defect once already

The last one is not speculative: it broke before. A thing that broke once and is untested is
the highest-yield item on this page.

### App lock — **none**

- [ ] All three strengths
- [ ] Gating **every** entry point: the overlay, the widget, the tile, the share target, the
      selection hook
- [ ] The lock-after timing
- [ ] The forgot-passphrase wipe and its confirmations

Entry points are the risk. A lock that guards the launcher icon and not the share target is
worse than no lock, because it is believed.

### Auto archive — **none**

- [ ] Each period
- [ ] The pinned exemption
- [ ] **Measuring from last activity rather than creation**
- [ ] The count shown before applying
- [ ] Undo restoring a whole pass
- [ ] The empty state when it archives everything

### Entry points — **none**

The widget, the quick settings tile, the share sheet target, the text selection hook. Each
from a cold start, a warm start, with the app already open, and with the app locked. Sixteen
combinations, zero observed.

### Discover beyond the card — **none**

- [ ] Drawing a moment, the quiz, saved moments, the trail
- [ ] Pack install and delete, storage accounting
- [ ] An exhausted pack
- [ ] The discussion surface itself, **blocked on #147**: the harness cannot reach it

### Voice — **none**

- [ ] Dictating a long rambling message
- [ ] Read-aloud, including stopping mid-sentence
- [ ] Switching voices
- [ ] Downloading and deleting a voice model

### Export and import round trip — **the codec half is well covered; the device half is not**

**This entry was wrong when first written, and the correction is instructive.** I recorded the
round trip as never verified, then found two tests that cover it thoroughly, and my evidence
for the original claim was a grep of the codec for keys written but never read. That grep
produced seven candidates and **every one was a false positive**: five are format metadata,
two (`saved`, `savedAt`) are deliberately read-only so a legacy pre-unification backup folds
its moments into follow-ups, and `rev`/`lastWriterId` are read through an accessor the regex
did not match.

So the instrument was mine, and it was the unreliable thing. Exactly the standing suspicion,
pointed at my own work.

**What is genuinely covered, at the codec level:**

- `BackupRoundTripTest` — codec round trips identically, crypto round trips, a wrong
  passphrase is rejected, garbage is not a backup, sync stamps survive, an older unstamped
  backup imports as unstamped rather than overwriting.
- `BackupFieldCoverageTest` — **every field set to something other than its default**, which
  is the right technique and not the obvious one. It exists because `linkedConversationId`
  was added by a migration later than the codec, export silently dropped the link between a
  chat and its Workbench session, and the plain round trip test could not catch it: a field
  left at its default round-trips perfectly through a codec that drops it, because the
  default is what comes back.

**What is not covered, and is the actual gap:** everything above the codec.

- [ ] Writing the file through the document picker
- [ ] The passphrase flow on the device, including a wrong one
- [ ] Choosing the file back through the picker
- [ ] **Merge versus replace**, which is a branch with no unit coverage at all
- [ ] Opening the exported file and confirming it holds real conversations, rather than
      trusting that export reported success

The last one is the gate on deleting anything.

### Onboarding end to end — **none on a fresh install**

Pieces are device-verified (#75, #78, #119, #121). The whole flow on a genuinely fresh install
is not, including the one-time mode explanation and the coach mark if it was built.

Costly: a fresh install means an uninstall, which destroys the database key and a five
gigabyte model. Recorded as needing the owner's deliberate decision rather than done casually.

### Search scope — **answered by reading, and it covers more than its label says**

Resolved without a device, since scope is a property of the queries. Filed as #148.

| kind | fields matched |
|---|---|
| conversations | `title`, `messages.content` |
| projects | `name`, `instructions`, `notes` |
| follow-ups | `snippet`, `note`, which reaches saved Discover moments after the unification |

So the audit's open question is answered: **it does cover project instructions and saved
Discover items.** Two findings came out of asking:

- The field is labelled "Search conversations" while the search reaches three kinds. An
  understatement rather than a false claim, and the rare direction where the app is more
  capable than it says.
- **Workbench artifacts are not searchable at all.** No `search` DAO, absent from
  `SearchResults`.
- Memory is not searched either, and that looks deliberate: the Memory screen lists everything
  in full. Recorded so nobody "fixes" it.

Escaping is handled (`ESCAPE '\'` on every `LIKE`), and `SearchQueryTest` covers the wildcard
class, so the sibling question is covered by unit tests rather than open.

### Workbench — **none**, blocked on #147

Not a chat mode, so nothing that types into a composer reaches it. Its contract is to return
only the transformed text, and the recorded prediction that a shared prompt rule would break
that is still unmeasured.

---

## Covered by unit tests only, and counted as unexercised

Listed so nobody mistakes a green suite for coverage.

- The relevance floor's standing rule, 24 negative cases: **unit**, with the two real store
  entries confirmed on device
- The partial-match guards after three fixes: **unit**, and the user-level check of the eight
  demonstrated short messages is still outstanding
- Prefix cost frequency, 17%: **unit**, deliberately, since selection is a pure function
- Disk exhausted during download: **unit**, with a stated reason not to reproduce it
- Backup codec field round trip: **unit**, and see the export gap above

## How this gets worked

Device runs queue in the background; the audit and issue writing happen while they run. Every
finding gets an issue with its blocking judgement stated when it is opened, and every area
above moves from **none** to **device** or gets an issue saying why it cannot.

Nothing here is claimed as working until it has been observed working.
