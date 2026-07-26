# Kam AI

[![CI](https://github.com/Kamsiob/kam-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/Kamsiob/kam-ai/actions/workflows/ci.yml)
[![Licence: AGPL-3.0](https://img.shields.io/badge/licence-AGPL--3.0-blue)](LICENSE)
[![Release](https://img.shields.io/badge/release-none%20yet-lightgrey)](https://github.com/Kamsiob/kam-ai/releases)

**A private thinking and drafting tool that runs entirely on your phone.**

Kam AI downloads an AI model onto your Android phone and runs it there. Your
conversations, memory, projects and follow-ups stay on the device. There is no
account, no subscription, no ads, and nothing is collected. Turn on airplane
mode and it still works.

> Kam AI is under active construction. This README describes what is built so
> far and is updated at the end of every phase.

## What it is, and what it is not

Kam AI is good at transforming, organizing and rephrasing text you give it, at
everyday questions, and at pushing back on your ideas.

**Kam AI is not a private ChatGPT.** A model small enough to fit on a phone
knows less than the big cloud models, gets some facts wrong, cannot make images,
and is weaker at long polished documents. The app says so plainly and builds
around it: anything worth checking gets a bookmark into Follow-ups instead of a
confident guess.

There are no characters, no roleplay, no pretend companion, and no emotional
manipulation. Those are stated design commitments, not just internal rules.

**It thinks with you, not for you.** That is the difference the modes are built
around, and it is why one of them refuses to hand you ideas at all.

## The four modes

One AI, four ways of working. A mode is chosen when a chat starts and can be
switched at any time.

| Mode | What it does |
|---|---|
| **General** | Everyday questions and back-and-forth. |
| **Logic Partner** | Argues the other side and pokes holes in your thinking. It concedes when you are right, and does not fold just because you disagree. |
| **Brainstorm** | Will not hand you ideas. It pulls them out of you, using a named facilitation method and asking one question at a time. |
| **Workbench** | Paste something in and get it rewritten, tightened, or reorganized. Sessions are saved and can be reopened, and each can be linked to a chat about its result. |

Discover is not a mode. It is a source: offline packs of short reads you can pull
one from and then discuss, with its own tab.

## Screenshots

Captured from the running app on a Pixel 10 Pro XL, never from a mockup. Light
and dark, since the app ships both and you will only ever use one of them.

| | | |
|---|---|---|
| ![Chats](docs/screenshots/chats-light.png) | ![A conversation](docs/screenshots/chat-light.png) | ![Discover](docs/screenshots/discover-light.png) |
| Chats, with the four modes below. Tapping one starts a conversation with that job | A conversation. Answers use headings and lists when the content calls for them, and plain prose when it does not | Discover deals you something to read, offline, and will discuss it without straying from the passage |
| ![Projects](docs/screenshots/projects-light.png) | ![Follow-ups](docs/screenshots/followups-light.png) | ![Choosing a mode](docs/screenshots/modepicker.png) |
| Projects keep related chats under shared instructions and notes | Follow-ups collects everything you saved, from anywhere in the app | Switching mode mid-conversation, without losing what was said |

<details>
<summary><b>The same six screens in dark</b></summary>

<br>

| | | |
|---|---|---|
| ![Chats, dark](docs/screenshots/chats-dark.png) | ![A conversation, dark](docs/screenshots/chat-dark.png) | ![Discover, dark](docs/screenshots/discover-dark.png) |
| ![Projects, dark](docs/screenshots/projects-dark.png) | ![Follow-ups, dark](docs/screenshots/followups-dark.png) | ![Choosing a mode, dark](docs/screenshots/modepicker-dark.png) |

</details>

## More than a chat box

- **Hold the power button** to ask something without leaving what you are doing.
  Speak or type; the answer arrives over the top of whatever is on screen.
- **Talk instead of typing.** Your voice becomes text on the phone itself, with
  whisper.cpp, and the model will tidy a rambling voice note into notes or a
  draft.
- **Attach a document** and ask about what is in it. The file never leaves the
  phone, and if it is longer than the model can hold, the app says so rather
  than quietly truncating it.
- **Projects** keep related chats together under instructions they all follow.
- **Follow-ups** is one bookmark for the whole app: anything worth checking or
  coming back to lands in the same list, told apart by where it came from.
- **Memory** keeps the durable things it notices. Everything it has kept is
  listed in full in Settings, and any of it can be deleted.
- **Read aloud** with an on-device voice, male or female, downloaded separately.
- **Backup and restore** writes everything to one passphrase-locked file, so
  moving to a new phone does not mean starting over.

## Install

Two ways to get it, and they are the same app.

**Google Play.** The usual route. Updates arrive on their own.

**GitHub releases.** For people who avoid the Play Store or run a de-Googled
device, every version is also published here as a plain APK you can download and
install directly. Grab the newest `.apk` from
[Releases](https://github.com/kamsiob/kam-ai/releases). The first time you open
an APK, Android will ask you to allow installs from whichever app you used to
open it, usually your browser or file manager. That is a one time permission for
that app, not for Kam AI.

The two builds are signed with different keys, so Android treats them as
separate apps. You cannot install one on top of the other. To switch, uninstall
the one you have first, then install the other. Your conversations can come with
you: use Settings, then Backup and restore to export a file before uninstalling,
and import it after.

## Building it yourself

You need the Android SDK with platform 37 and NDK 28, plus a JDK 21. Then:

    git clone https://github.com/kamsiob/kam-ai.git
    cd kam-ai
    ./tools/fetch_llama.sh          # pulls llama.cpp at the pinned tag
    ./tools/fetch_whisper.sh        # pulls whisper.cpp for voice typing
    ./tools/fetch_sherpa.sh         # pulls the sherpa-onnx voice runtime and data
    ./gradlew :app:assembleDebug

The three fetch scripts pull the native dependencies (llama.cpp, whisper.cpp, and
the sherpa-onnx text-to-speech runtime) at pinned versions, kept out of git to
keep the tree small. They are the only setup step beyond the SDK.

llama.cpp is fetched rather than committed, so a clone stays small. The tag it
pins lives in `tools/fetch_llama.sh`.

To run the native smoke test on a connected phone, which loads a tiny model and
generates tokens through the JNI bridge:

    ./tools/fetch_smoke_model.sh
    ./gradlew :app:connectedDebugAndroidTest

## How it is put together

The short version: Kotlin and Jetpack Compose, single activity, Material 3 with a
fully custom theme and no dynamic colour, because the palette carries meaning.
llama.cpp compiled for arm64 behind a thin JNI bridge, with the generation loop in
Kotlin so streaming, stopping and thermal backoff sit next to the rest of the
logic. One SQLite database through Room and SQLCipher holds everything, shaped so
a backup is a single portable file.

[ARCHITECTURE.md](ARCHITECTURE.md) has the long version: the components and their
responsibilities, how inference is integrated, how data is stored and protected,
the threading and lifecycle model, and where the significant constraints come
from.

## Approach

The app is specified before it is built. [MASTER_SPEC.md](MASTER_SPEC.md)
describes what it does and [DESIGN.md](DESIGN.md) describes how it looks, moves
and speaks, down to the copy. Both are kept current with the code rather than
written once at the start; where the code and those documents disagree, the
documents are the source of truth and the code is wrong.

Work is tracked in the open. The
[project board](https://github.com/users/Kamsiob/projects/1) is public and holds
every issue, open and closed, with what platform it belongs to and what state it
is in. Nothing is marked done until it has been verified on real hardware.

Decisions are recorded as they are made. [DECISIONS.md](DECISIONS.md) holds every
nontrivial architectural and product call along with the reasoning, including the
ones that turned out to be wrong and the approaches that failed and should not be
retried. Two feature requests are recorded there as declined, with the arithmetic
that rules them out, so they do not get re-proposed from scratch.

The [issue tracker](https://github.com/Kamsiob/kam-ai/issues) is the authoritative
record of state. An issue states the current situation, why it matters, and
acceptance criteria in checkable terms, so that closing one is verifiable rather
than a judgement call. Working notes go on the issue as the work happens.

Nothing is marked finished until it has been verified on real hardware. Unit tests
are necessary and not sufficient: several defects in this codebase compiled,
passed their tests, and were wrong on the device, and a few of the most useful
comments in the source record exactly that.

[HANDOFF.md](HANDOFF.md) is maintained so the project can be picked up cold, with
the measurements taken, the approaches that failed, and an honest state of every
unfinished thing.

### How this is built

Claude Code writes the implementation. I do the design, the specifications, the
decisions, and the verification.

That split holds all the way down. I decide what gets built and what gets cut,
write the specification each piece is built against, record the architectural and
product decisions along with the reasoning, and confirm everything on real
hardware before it counts as finished.

The harder part turned out to be directing it. Long unsupervised runs fail in
specific ways. They stop early believing the work is complete, run out of working
context mid task, report success for code that was written but never run, and
occasionally take an action nobody asked for. Most of the process described above
exists because of one of those. The resume document, the rule that nothing is
finished until it runs on the device, the decision records, the limits on what the
agent may touch: each answers something that actually went wrong.

What it has given me is precision about what I am asking for, and an unwillingness
to accept finished without seeing it work.

## Licence

App code is AGPL-3.0. See [LICENSE](LICENSE).

Content packs for Discover are built from Wikipedia and carry CC BY-SA 4.0,
which applies to the pack content only.

## Project

- [Board](https://github.com/users/Kamsiob/projects/1): what is being worked on, what is blocked, and what has shipped
- [Roadmap](https://github.com/Kamsiob/kam-ai/issues?q=is%3Aissue+label%3Aroadmap): what is planned and what is deliberately not
- [Issues](https://github.com/Kamsiob/kam-ai/issues): the authoritative record of state
- [Contributing](CONTRIBUTING.md), [Architecture](ARCHITECTURE.md), [Security](SECURITY.md), [Code of conduct](CODE_OF_CONDUCT.md)

## Links

- Privacy policy: [PRIVACY.md](PRIVACY.md)
- YouTube: [@kamsiob](https://youtube.com/@kamsiob)
- Website: [kamsiob.com](https://kamsiob.com)
- Feedback: hello@kamsiob.com

Built and carried by one person. If software made this way matters to you,
there's a place to
[stand behind it](https://buymeacoffee.com/kamsiob). Either way, it's yours.
