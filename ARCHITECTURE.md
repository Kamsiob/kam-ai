# Architecture

How Kam AI is put together, for someone who wants to understand or change it.
Read [MASTER_SPEC.md](MASTER_SPEC.md) for what it is meant to do and
[DECISIONS.md](DECISIONS.md) for why particular choices were made, including the
ones that look wrong from outside.

Roughly 120 Kotlin files in `app/src/main/java`, four C++ translation units in
`app/src/main/cpp`, 59 JVM test files and 14 instrumented ones.

## Shape

One Android application module. No multi-module split, because the app is one
screen graph over one database and one inference engine, and module boundaries
would be ceremony at this size.

```
MainActivity ─ KamAiApp (the whole screen graph, one Compose tree)
                  │
                  ├── AppViewModel ──────── KamRepository ── KamDatabase (Room + SQLCipher)
                  │        │                      │
                  │        │                      └── Downloader ── Downloads (foreground service)
                  │        │
                  │        └── ModelManager ── InferenceEngine ── LlamaBridge ─┐
                  │                                                            │ JNI
                  ├── ChatViewModel (one per conversation, keyed by id)         │
                  ├── WorkbenchViewModel                                  libkamai.so
                  └── DiscoverViewModel                                  (llama.cpp b10058)

OverlayActivity ─ OverlayViewModel ─ the same ModelManager and InferenceEngine
```

Two entry points share one engine: the main activity and the assistant overlay
reached by holding the power button. `Models.engine()` and `Models.manager()`
return process-wide singletons, so there is never a second model resident.

## Inference

`llama.cpp` is built from source by CMake as part of the Gradle build, pinned to a
specific upstream commit. `kamai_llama.cpp` is the only JNI surface: load, ingest,
next token, save state, restore state, and a handful of context queries. Nothing
above it knows about llama.cpp types.

Three things in that file carry most of the difficulty.

**One sequence, diffed every turn.** The context holds the exact token list
currently cached. On the next turn the new prompt is diffed against it and only the
divergent suffix is decoded, so a long conversation does not re-read its whole
history each turn. Getting the bookkeeping wrong here does not crash, it answers
from the wrong history, so the file checks `llama_memory_seq_pos_max` against its
own record after every ingest and clears both rather than trust a mismatch.

**State that outlives the process.** `nativeSaveState` returns the sequence's KV
data plus the tokens describing it. Kotlin encrypts that before it touches disk.
Reopening a conversation restores it instead of re-prefilling: measured on a real
conversation, 656 tokens and 20.4 seconds became 29 tokens and 2.0 seconds.

**Two-stage memory pressure.** The context and its KV cache can be freed while the
memory-mapped weights stay, and recreated later without reloading gigabytes.
`ModelManager` drives that from `onTrimMemory` and a thermal watcher.

`InferenceEngine` owns a single-threaded dispatcher; every native call goes through
it, because two decodes on one context is the failure mode this design exists to
prevent.

## Data

Room over SQLCipher. The database key is generated in the Android Keystore,
StrongBox-backed where the device has it, and never leaves it. `DatabaseKey` also
exposes streaming encryption for bulk data, which uses a per-file random data key
that the Keystore wraps: sending megabytes through a StrongBox cipher directly
produced thirteen bytes in twenty seconds, which is documented in DECISIONS.md
because it is not obvious until it happens.

Schema at version 8. Every migration ships as an exposed list of SQL statements so
a pure JVM test can drive the exact statements that ship rather than a copy of
them, including an interrupted migration rolling back and re-running.

Everything the UI reads is a `Flow` from a DAO, collected as state. There is no
in-memory cache of database content, and no repository-level mutable state that
could disagree with the tables.

## Modes

A mode is a system prompt and nothing else. `SystemPrompts` composes one from
shared hard rules plus a per-mode method, then layers user instructions, project
instructions and notes, retrieved memory, the date, and any attachment, in that
order. The order matters and is tested: the hard rules must come first so
everything after them is subordinate.

`PromptBudgetTest` caps each mode's system prompt in estimated tokens. Raising a
cap is allowed and has to be argued for in the test file itself, which is why the
comments there read like a record.

Modes are data, not subclasses. Adding one means adding an enum value, a color, and
a prompt.

## Threading and lifecycle

- Compose UI on the main thread, state from `StateFlow` collected with lifecycle
  awareness.
- Database work on Room's own executors, reached only through suspending DAO
  functions.
- Inference on `InferenceEngine`'s single dispatcher.
- Downloads in a foreground service, so they survive backgrounding, with partial
  files resumed rather than restarted.
- A `ChatViewModel` is keyed by conversation id, so navigating away and back
  reuses its state, and a new chat gets a fresh key rather than inheriting the
  last one.

Generation's teardown runs in `NonCancellable`. An answer interrupted by the
screen going away must still be marked incomplete with a reason, and every call in
that path suspends, so an unwrapped block would throw at its first suspension
point and leave a message that is a lie.

## Where the constraints come from

Most of the awkward parts of this codebase come from three facts.

**The model is on the phone.** Memory is the binding constraint, so only one model
is ever resident, the context is sized below the model's trained maximum, and the
KV cache is quantised. Two features have been declined outright because they need a
second model resident; both are recorded in DECISIONS.md.

**The model is small.** It misremembers facts, so the app is built to say so, to
make bookmarking easy, and never to present recall as authority. It also follows
demonstrated shapes far better than described rules, which is why the formatting
guidance in the system prompt contains worked examples rather than instructions.

**Nothing leaves the device.** No analytics, no crash reporting service, no remote
config. That removes a whole class of ordinary Android machinery and means bugs are
found by using the app on a real phone, which is why the verification standard is
what it is.
