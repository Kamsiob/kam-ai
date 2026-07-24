# DECISIONS.md

A running record of every nontrivial decision made while building Kam AI, and of
anything that could not be done without the owner. Newest phase at the bottom.

## Phase 0 · Repository and scaffolding

### Secrets

The Play service account JSON key was sitting in the project folder at the start
of the run (`kamsiob-503213-159c76e1da43.json`). It was moved, before git was
initialised, to:

    ~/.kamsiob-secrets/play-service-account.json   (mode 600, directory mode 700)

It is outside the repository and is also covered by `.gitignore` patterns as a
second line of defence. Nothing references it by content, only by path. The
release keystore will be generated into the same directory in Phase 8.

`.gitignore` was written as the first file in the repository, before any source
existed, and covers `*.jks`, `*.keystore`, `keystore.properties`,
`local.properties`, and every service-account filename shape.

### Build environment

The machine had a partial Android SDK and no JDK, Gradle, NDK, or CMake. This is
an image-based Fedora system (`rpm-ostree`), so `/usr` is read only and system
package installs are not an option. Everything was installed into the home
directory instead:

- JDK 21 (Homebrew `openjdk@21`). Chosen over 17 because AGP 8.x+ and Kotlin 2.x
  both run happily on 21, and 21 is the current LTS.
- Gradle via Homebrew, used once to generate the project's Gradle wrapper. After
  that the wrapper is the only build entry point, so the repo is self contained.
- Android command line tools, NDK, and CMake into `~/Android/Sdk`.

### SDK levels

- `targetSdk = 36` (Android 16). Verified against Google Play's published policy
  rather than trusting the prompt: from 31 August 2026, new apps and updates must
  target API 36 or higher. Since this app is being submitted after that date,
  36 is the floor, not a choice.
- `compileSdk = 37`. See the build system note below.
- `minSdk = 31` (Android 12). `ROLE_ASSISTANT` is available from API 29, so 29
  would have been possible, but 31 was chosen because every device with enough
  RAM to run a local language model at a usable speed is on Android 12 or newer,
  and 31 gives predictive back, modern `PowerManager` thermal APIs used by the
  battery and thermal work in Phase 1, and themed icons without compatibility
  shims. The cost of this choice is close to zero real devices.

### NDK and CMake

- NDK 28.2.13676358 rather than the newer 29.x line. 29 is published as stable
  but llama.cpp's Android CI and its documented build path are settled on the 28
  series, and there is nothing in 29 this project needs. Reversible: it is one
  line in `gradle.properties`.
- CMake 3.31.6 rather than the 4.x line available in the SDK manager. CMake 4
  removed compatibility with `cmake_minimum_required` values below 3.5, which
  still breaks a number of vendored dependency trees. 3.31 is the last of the 3.x
  line and builds llama.cpp without special handling.

### Repository conventions

Matched to the other kamsiob repositories (`dig`, `logbook`, `bearings`):
description is one line naming what it is and who it is for, followed by the
local-first and no-telemetry statement; topics are lowercase kebab case and
include the licence tag; README leads with the name, a bold one line positioning
statement, a plain paragraph, then screenshots, then what it is and is not.

### Build system surprises worth recording

AGP 9 compiles Kotlin itself. Applying `org.jetbrains.kotlin.android` alongside
it is now a hard error, so it is not in the plugin list. The Compose compiler
plugin is still required and is applied. Because AGP 9.3.0 carries Kotlin 2.2.10
internally, the Compose plugin and KSP both have to match that exact version,
which is why Kotlin is pinned at 2.2.10 rather than the newer 2.4.10 that exists.
Trying 2.3.10 first was the wrong guess and cost one build cycle.

KSP 2.0.x still registers its generated sources through the Kotlin source set
DSL, which AGP 9 rejects by default. `android.disallowKotlinSourceSets=false` in
`gradle.properties` allows it. The generated Room code lands in the right place
regardless. That line should come out once KSP supports built-in Kotlin.

`compileSdk` is 37 rather than 36 because current AndroidX libraries refuse to
be compiled against anything older. `targetSdk` stays at 36, which is what Play
requires. Compiling against a newer platform than you target is supported and is
the lower risk option, since targeting 37 would opt the app into Android 17
runtime behaviour changes that nothing here has been tested against yet.

### The mark

The ring gap was wrong in the first build. Compose and SVG both measure angles
clockwise from three o'clock, so starting the 270 degree sweep at 45 degrees put
the opening at three o'clock rather than the top right. Sweeping 0 to 270 puts it
where DESIGN.md says. Caught by taking a real screenshot off the phone and
looking at it, which is worth doing rather than trusting the arithmetic.

### The smoke test

The tiny model travels inside the test APK as an asset and is copied to a real
file at the start of the run, because llama.cpp needs a path on disk. The first
attempt pushed it to the app's external files directory over ADB, which modern
Android blocks for the shell user, and the resulting failure was disguised as an
`UnsatisfiedLinkError` from `@After` because a skipped `@Before` still runs
teardown. Teardown is now guarded.

`klosax/tinyllamas-stories-gguf` was the obvious source but ships GGUF version 1,
which current llama.cpp cannot read. `ggml-org/models` has the same model at
GGUF version 3.

## Release process, permanent

Every release of Kam AI publishes two artifacts, and this is not optional:

1. **The Play Store bundle.** A signed AAB uploaded through the Android
   Publisher API, with Google Play App Signing holding the app signing key and
   the local keystore acting as the upload key.
2. **A plain APK on GitHub.** A universal release APK, signed with the same
   local release keystore, named for the app and version
   (`kam-ai-<version>.apk`), attached as an asset to a GitHub release tagged
   with that version, with release notes.

The GitHub APK exists for people who avoid the Play Store or run de-Googled
devices. It is not a beta channel or a lesser build; it is the same version.

The release notes must always carry a plain line saying that the GitHub APK and
the Play Store version are signed differently, so Android treats them as
separate apps and switching between them means uninstalling one before
installing the other, and that conversations can be carried across with the
app's own Backup and restore.

The README keeps an Install section covering both paths, including the note that
Android asks for permission to install from the browser or file manager the
first time.

After publishing, the APK is downloaded back from the GitHub release and
verified: installed and launched on the phone when one is connected, and its
SHA-256 compared against the built artifact either way.

This was added as a standing instruction partway through the build and applies
from the first release onward. It is automated in `tools/cut_release.sh`, which
is written in Phase 8 when the release keystore first exists.

## Model family: Gemma first, then Qwen

The owner asked for Gemma preferred, then Qwen, taking whichever current variant
fits each tier's parameter band. Checked against the live Hugging Face API
rather than assumed. Gemma 3 is published at 1B, 4B, 12B and 27B, with nothing
between 4B and 12B, so:

- Basic, 1 to 2B: Gemma 3 1B Instruct, 806 MB at Q4_K_M.
- Balanced, 3 to 4B: Gemma 3 4B Instruct, 2.5 GB at Q4_K_M.
- Best Available, 7 to 8B: Qwen3 8B, 5.0 GB at Q4_K_M.

Best Available stays on Qwen because there is no Gemma in the 7 to 8B band at
all. Reaching up to Gemma 3 12B was considered and rejected: at Q4_K_M it is
about 7.3 GB, and on the 16 GB phone this tier targets that leaves nothing like
the 4 GB of headroom the tier logic insists on, so the app would be killed in
the background constantly. Falling back to Gemma 3 4B for the top tier was also
rejected, because then Balanced and Best Available would ship the same model and
the tier would be a lie.

Licences now differ across tiers, which is worth being straight about. Gemma is
under the Gemma Terms of Use: redistribution and commercial use are permitted,
but conditions including a use policy travel with the model. Qwen3 is Apache-2.0
outright. Nothing is bundled into the app. Every model is downloaded by the user
from its official repository, which both licences allow plainly. Both appear on
the Licenses screen.

### Two prompt formats

Shipping two families means the prompt layout can no longer be hardcoded, so it
moved into a `ChatFormat` enum that travels on each `TierModel`.

Gemma and Qwen differ more than cosmetically. Gemma has no system role at all,
so the guardrails have to be folded into the first user turn, which is what its
own template does. Qwen has a real system role but will reason at length before
answering unless the thinking block is closed before it opens, which on a phone
means a long wait staring at a typing indicator instead of a streaming reply.

This is the kind of bug that never crashes. A wrong layout produces quietly
worse answers and lets the guardrails slide off, so it is pinned by tests rather
than left to inspection.

## The privacy policy has one home

The policy is now live at its canonical address, <https://kamsiob.com/kam-ai-privacy.html>,
and that is what Google Play points at.

- `PRIVACY.md` was aligned to it word for word, verified by diffing the two
  texts token by token rather than by reading them. The only difference left is
  the website's own header and footer chrome, which is not policy text. The
  effective date is 22 July 2026 in both.
- The GitHub Pages copy at <https://kamsiob.github.io/kam-ai/> was reduced to a
  pointer at the canonical address. It had been a full second copy, which is
  exactly how two versions of a privacy policy end up quietly disagreeing.
- The app has a single `Links.PRIVACY` constant so the About row and the store
  listing cannot drift apart.

## Network discipline, audited

The policy's strongest claim is that the app makes no network request unless the
user asks for one. That was audited rather than assumed, statically and at
runtime.

**Static.** The whole app has exactly one network call site: `Downloader.kt`,
reached only from an explicit download action. There are no analytics, crash
reporting, ad, or tracking dependencies, direct or transitive; the resolved
release classpath was searched for Play Services, Firebase, Crashlytics and ad
libraries and is clean. There is no update check and no prefetch at launch.

**A real finding.** The merged manifest was carrying `RECEIVE_BOOT_COMPLETED`,
`WAKE_LOCK` and `FOREGROUND_SERVICE`. None of those were declared by hand. They
came from WorkManager, which had been added to the dependency list speculatively
and was never used by a single line of code. An app that promises it only
touches the network when asked has no business also asking to start itself at
boot. WorkManager was removed, along with `ACCESS_NETWORK_STATE`, which nothing
read either. The release manifest now requests exactly one permission: INTERNET.

**Runtime.** Verified on the Pixel 10 Pro XL over a cold start followed by
ordinary navigation, with nothing tapped that implies a download:

- socket file descriptors held by the app process: 0
- rows in the kernel TCP and UDP tables owned by the app's uid: 0
- bytes attributed to the app's uid by the platform network stack: 0 received,
  0 sent

**What this audit does and does not cover.** It covers the build as it stands.
The Discover packs sheet, which is the one surface designed to fetch a manifest,
does not exist yet, so the rule that its manifest is fetched only when the sheet
is opened is currently a design commitment rather than a verified fact. This
audit has to be repeated at Phase 8 with every surface present. The permission
side is now guarded permanently by `PrivacyClaimsTest`, which runs on device and
fails if a dependency ever reintroduces a background or advertising permission.

One wrinkle worth recording: the installed package also reports
`ACCESS_LOCAL_NETWORK`, which appears in neither merged manifest. Android
injects it from version 16 onward for apps holding INTERNET that have not opted
into the new local-network model. It is allowlisted explicitly in the test, with
a note, rather than filtered silently, so that the day it stops being platform
noise the test starts failing again.

## Store assets

`tools/make_store_assets.py` renders the 512 by 512 icon and the 1024 by 500
feature graphic directly from the DESIGN.md section 2 geometry and the section 3
palette, into `store-assets/`. Nothing is traced by hand and nothing is a
mockup, so the store graphics cannot drift from the app.

Two rendering bugs were found by looking at the output rather than trusting the
arithmetic, which is the second time in this build that has paid off. Pillow
grows an arc's stroke inward from its bounding box rather than centring it on
the radius, so the rounded end caps had to move to the stroke centreline instead
of the nominal radius, where they had been bulging outside the ring. And drawing
the core as stacked ellipses produced a soft halo past the core radius, which
reads as a glow; DESIGN.md permits no glow beyond the mark's breathing shadow
and the onboarding ripples, so the core is now rasterised per pixel and keeps a
hard edge.

## Back navigation

**The bug.** The system back gesture closed the app from every screen instead of
stepping back one level. Nothing in the app consumed the back event, so it fell
straight through to the activity. It was found the same way most of this build's
real bugs have been found: by driving the actual phone, in this case swiping
back while trying to take a screenshot and landing on the launcher.

**The fix.** Handlers are registered through the AndroidX back dispatcher by
whichever surface owns the dismissible state, rather than by one central
interceptor. The dispatcher already resolves innermost-first, so the priority
order falls out of where each handler is declared instead of being maintained by
hand in a growing when-block. A central handler would have to know about every
piece of transient state in the app and would be wrong the moment a new one
appeared.

The resulting order: an open dialog, then an open swipe row, then the navigation
stack, then the bottom navigation tab, then the app.

**Edge case, documented as required.** With an empty stack on a tab other than
Chats, back returns to Chats rather than leaving the app. Chats is the home
root. Somebody who wandered into Follow-ups and pressed back almost never means
"close the app", and the cost of guessing wrong in that direction is losing
their place; the cost of guessing wrong the other way is one extra press. Only
Chats with an empty stack declines to consume the event, which is what lets the
activity finish.

**Predictive back.** Implemented at the same time, since the handler had to be
registered either way. The outgoing surface scales down slightly and slides
toward whichever edge the gesture started from, revealing the destination
underneath, and commits with the directional slide DESIGN.md specifies. It uses
the damped spring, not the expressive one: this is navigation, and overshoot is
reserved for signature moments. Under reduced motion no progress is reported at
all, so nothing moves under the finger and the change lands as a plain swap.

**Verified on the phone**, not only in tests: Licenses, back to About, back to
Settings, back to Chats, back to the launcher. Four presses, four correct
destinations, and the app is left only from the home root.

## The download that stalled at 45 percent

A real 2.5 GB model download died at 1.1 GB and then sat there indefinitely
showing no progress, no error, and no way forward.

The cause was a line written earlier in this build with a confident and wrong
comment. OkHttp's read timeout had been set to zero, meaning wait forever, on
the reasoning that a phone pulling several gigabytes over a slow connection is
not an error and should not be killed at an arbitrary minute count. That
conflated two different things. Read timeout is the gap allowed between two
successive reads, not a budget for the whole transfer, so a generous value never
punishes a slow-but-progressing download. Setting it to zero only removes the
one mechanism that notices a connection has silently died, which on mobile
happens constantly as a phone moves between networks.

Now: a 60 second read timeout, which catches a dead connection while leaving a
slow one alone, and no call timeout at all, since that is the value that would
actually punish a slow connection.

The retry was also wrong in spirit. Requiring someone to notice a stall and
press retry on a twenty minute download is not a download experience, so a
dropped connection is now retried automatically, resuming from the bytes already
on disk via the existing Range request, with backoff, and only surfaces as a
failure once the retries are genuinely spent. A blip the user did not cause and
cannot act on is no longer shown to them at all.

## Combined update, part 1: Gemma 4, accents, theme, and polish

This large combined update lands across several parts. The contained,
independently testable pieces are done and verified first; the larger items
(at-rest encryption and app lock, the shared confirmation and bulk-select
system, memory modes, sharing, the Workbench surface) are substantial and are
being built in sequence after this checkpoint, in phase order where they belong.

### Gemma 4 across every tier (PART 1)

Gemma 4 is real and current, released 2 April 2026, and was verified directly
against the live Hugging Face API rather than taken on faith: it is Apache 2.0
(not the older Gemma Terms of Use), ships instruction-tuned GGUF, and its size
range finally covers the whole envelope. The lineup is now Gemma 4 only:

- Basic, 8 GB: Gemma 4 E2B, 3.1 GB at Q4_K_M.
- Balanced, 12 GB: Gemma 4 E4B, 5.0 GB at Q4_K_M.
- Best Available, 16 GB: Gemma 4 12B, 7.1 GB at Q4_K_M.

There is no Qwen anywhere any more. Gemma 4's range fills the 7 to 8 billion
band that once forced Qwen at the top, so the app is one family, one licence
(Apache 2.0, no asterisk on any tier), and one prompt format. Sizes and SHA-256
hashes were read from the API, not documentation. E2B was downloaded onto the
Pixel, hash-verified to the exact byte, and an instrumented test loaded it
through the bridge and confirmed it answers "name three primary colours" with a
real primary colour, which is the difference between tokens coming out and the
model actually understanding the Gemma 4 prompt format.

The prompt format stays the Gemma `<start_of_turn>` layout, which Gemma 4 shares
with 2 and 3. The ChatFormat enum keeps the per-model design so a future family
still drops in cleanly.

An Advanced model list (PART 2) is seeded in the catalogue (a higher-quality E4B
quantisation, verified) and will be surfaced in Settings alongside the rest of
PART 2 (multiple installed models, switching, safe deletion of the active one).

### Sixteen accent colours, verified in both themes (PART 8)

The accent is now user-chosen from sixteen colours, eight bright and eight
earthy, green the default. Every colour was designed and checked by a contrast
script before it was allowed into the code, and each has a separately tuned
light-theme and dark-theme shade plus its own on-accent text colour. Every one
clears the same bar in BOTH themes: on-accent text at 4.5:1, and the accent
against the background at 3:1. None had to be dropped; the first search maximised
contrast and made them all dark and samey, so it was retuned to prefer vividness
subject to passing, which is why bright colours are saturated and earthy ones
muted while all still pass. The default green keeps the exact DESIGN.md section 3
values, tonal shades included. AccentContrastTest pins all of this so a future
tweak that breaks a colour in one theme fails the build.

Theme mode is System, Light, or Dark, and both theme and accent live in a small
SharedPreferences file read synchronously before the first frame, so there is no
flash of the wrong appearance at launch. They are device-local display
preferences, not user content, and the backup will include them by reading this
store at export time.

The accent never touches the reserved amber, which stays fixed for bookmarks,
locked tiers, and the support button whatever the accent.

### The bookmark, not the flag (PART 5, first piece)

The follow-up action under an AI response was a flag, sitting a few icons from
Report, and the two were too easily confused. It is now a bookmark, filled and
amber when set, outline when not. The meaning is unchanged. The Follow-ups nav
tab uses the same bookmark for consistency. Report keeps its own distinct place
in the overflow.

### A safety notice (PART 9) and wider bubbles (PART 4, first piece)

Settings gains a plain, non-alarmist "Kam AI can be wrong" notice in the app
voice: the model is small and gets things wrong, check what matters with the
bookmark and Follow-ups, and it is not a substitute for a professional. Chat
bubbles widened from a fixed 320dp to about 80 percent of screen width, so a
long answer reads like prose while the messaging shape and left/right asymmetry
stay.

## Combined update, part 2: the shared confirmation system (PART 0)

Every destructive action now runs through one component, ConfirmDialog, rather
than a dialog per screen. It has two tiers that look identical and differ only in
how hard they are to confirm:

- Tier one, one tap, for routine single-item deletions: one chat, one memory, one
  follow-up, one download. Deleting a chat is never a two-step gauntlet.
- Tier two, two steps, for irreversible bulk loss: delete-all, batch delete, the
  data wipe. The first step asks; the second states plainly it cannot be undone.
- Tier two with a typed confirm word, for the very largest wipes (delete
  everything, and later the forgot-code reset), so they can never be a single
  easy tap. The confirm button stays disabled until the word is typed.

Destructive styling is the amber label on a plain surface, which is amber's
legitimate destructive use; nothing else in the dialog borrows it.

A Settings toggle, on by default, controls whether deleting a single chat asks at
all. Off means one swipe-and-tap deletes it, for people who clear chats often and
do not want the friction. It applies only to single chat deletion; a bulk delete
of several chats is always tier two.

Three instrumented tests pin the behaviour: tier one confirms in one tap, tier
two needs the second step, and the largest wipe does nothing until the word is
typed.

### A gap found and closed

The old delete-everything dialog had a checkbox to also delete downloaded models.
Folding that into the shared component would have meant a bespoke variant, so
delete-everything is now data-only: it wipes conversations, memory, projects,
follow-ups and Discover state, and leaves the downloaded models in place, where
they are deletable individually in Storage (each a tier-one confirm). This is
also the safer default, since re-downloading several gigabytes is a real cost and
"delete my data" rarely means "and make me download the model again".

### Still to come in this combined update

Bulk multi-select mode on the lists (the selection UI itself), the Advanced
models section and multi-model management, at-rest encryption and the app lock,
memory modes, sharing, chat rename, the in-chat mode toggle redesign, and the
Workbench surface. The confirmation and bulk-delete plumbing they need is now in
place; deleteConversations and forgetMany already exist and are wired to tier
two, waiting on the selection UI.

The visual polish pass on the new surfaces (accent picker, appearance screen) is
folded into the Phase 8 screen-by-screen review, which is the designated place
for that audit.

## Combined update, part 3a: at-rest database encryption (PART 3)

The database is now encrypted at rest with SQLCipher community edition. The whole
store, conversations, memory, projects, follow-ups and Discover state, is
meaningless if the file is copied off the device at a repair counter or during a
transfer. This is always on, transparent, and asks nothing of the user.

The key. The database passphrase is 32 random bytes, generated once on the
device and never derived from anything typed. It is wrapped by an AES-256-GCM
key in the Android Keystore, StrongBox-backed where the phone has the chip and
TEE-backed otherwise, which never leaves secure hardware and cannot be exported.
The wrapped passphrase sits in a small sandbox file that is useless on its own.
A device that advertises StrongBox but cannot honour the key spec falls back to
a standard hardware key rather than failing to open the database at all.

The migration is the part that had to be right, because an existing install has
a real plaintext database full of someone's conversations. The safe direction,
found the hard way, is to open the new encrypted database with the key, attach
the old plaintext one with an empty key, and export its contents in with
sqlcipher_export. Opening a framework-created plaintext file as a primary
SQLCipher connection fails with "file is not a database" whether the empty key is
given as a byte array or a string; the attach-and-export direction is what
SQLCipher actually supports. The plaintext original is never deleted until the
encrypted copy exists at the final path and its per-table row counts have been
verified against the source, and a half-finished migration leaves a staging file
that is detected and discarded on the next launch, restarting cleanly from the
untouched plaintext.

Verified on the Pixel with four instrumented tests: seeded plaintext data
survives migration intact, the migrated file contains none of the secret content
as readable bytes and no longer carries the SQLite header, the wrong passphrase
cannot open it, and an interrupted migration recovers. The real app was then run
end to end on device: it launches with no crash, the on-disk header is random
bytes, and the Keystore-wrapped key file is present.

DatabaseKey.destroy tears the key and wrapped passphrase away, which the
forgot-code wipe will use: the encrypted data is not recovered, it is made
permanently unopenable, and a fresh passphrase is minted next launch.

Still to come in PART 3: the encrypted backup file, the optional app lock across
every entry point, and the forgot-code wipe-and-restart flow.

## Combined update, part 3b: the optional app lock (PART 3)

An optional lock on Kam AI itself, separate from the phone's lock, off by
default. Two honestly-labelled strengths, and the tradeoff is spelled out at the
moment of choosing rather than buried.

- Device mode is backed by the phone's own credential through the system
  biometric prompt: fingerprint, face, or the phone PIN or pattern. It is
  recoverable, because the device credential always works, and it is the simpler
  choice. It is a gate on the app on top of the always-on at-rest encryption. It
  is honestly the slightly weaker option against someone who already knows the
  phone code, and it is labelled as such.

- Passphrase mode is a separate passphrase known only for Kam AI, and it is
  genuinely stronger because it gates the database key itself, not just the UI.
  The key file gains a PBKDF2-derived AES-GCM layer over the Keystore-wrapped
  bytes, so without the passphrase the database cannot be opened at all, not even
  by someone with the phone unlocked and its code. No passphrase is ever stored:
  the proof it is right is that it unwraps the key. That is what makes a
  forgotten passphrase genuinely unrecoverable.

The security model, stated plainly. At-rest encryption (part 3a) protects a
database file copied off the device. The app lock protects against someone
holding the unlocked phone. Device mode is a UI gate plus that at-rest
encryption; passphrase mode additionally locks the key, which is the honest
difference between "slightly weaker but recoverable" and "stronger but
unrecoverable" that the two labels promise.

Forgot-code recovery. Nobody is permanently locked out of a usable app. The lock
screen has a plain, non-nagging "Forgot your passphrase?" path that runs the
tier-two confirmation with a typed word ("erase"). It is honest about what it
does: it does not recover or reveal the old data, which would defeat the point;
it destroys the key so the encrypted data becomes permanently unopenable, clears
the database, turns the lock off, and leaves a working fresh app. A backup, if
they have one, is the only way back.

Timeout. A session stays unlocked for two minutes in the background by default,
so a person is not re-authenticating constantly within one sitting, and re-locks
once that passes, dropping the passphrase from memory when it does.

Architecture. The whole app, including the database, is gated behind the lock in
KamRoot at the very top. When locked, nothing that can read or add data renders,
and in passphrase mode the database is not even opened until the passphrase is
supplied. MainActivity became a FragmentActivity so the biometric prompt can
attach to it, and drives the timeout from its start and stop lifecycle.

Verified: 6 instrumented tests on the passphrase layer (wrong passphrase fails,
a locked key cannot open with no passphrase, rewrap round-trips, destroy is
permanent and irreversible), 7 unit tests on the lock state machine (off by
default, a brief background trip does not re-lock, crossing the timeout does and
drops the secret), and the app launches cleanly as a FragmentActivity.

Two honest limits recorded. The biometric device-mode prompt cannot be automated
(there is no way to supply a fingerprint over ADB), so its final visual pass is
owner-verifiable; the logic it gates is tested. Biometric convenience in
passphrase mode (unlocking the strong lock with a fingerprint) would mean storing
the passphrase under a biometric-bound Keystore key; for now the strong mode is
passphrase-only, which is the simplest honest form of "unrecoverable", and the
convenience can be added later without changing the model.

### Deferred within PART 3, by the phase-order rule

The encrypted backup file is part of Backup and restore, which is master-spec
Phase 7. Per the instruction to integrate later-phase features into their phase
rather than build them early, the backup will be encrypted when it is built in
Phase 7; the passphrase-unrecoverable warning and graceful wrong-passphrase
handling are noted as Phase 7 requirements. The app lock gating of the other
entry points (the assistant overlay in Phase 4, and the widget, tile, share
target and text-selection hook in Phase 6) integrates into those phases as each
surface is built; the gating mechanism (AppLock.locked and KamRoot) is in place
for them to reuse.

## Combined update, part 4: multi-model management (PART 2)

The default experience is unchanged and effortless: pick a tier, done. On top of
that, an Advanced section on the Model screen, visually separated and clearly
optional, lists other compatible models a curious user can download and switch
between. Nothing there is required reading.

Switching the active model is now real rather than a toast: it sets the active
model in the database and loads it into the engine straight away, keeping every
conversation, since only the thing answering them changes.

Deleting the active model can never leave the app with nothing usable, which was
the workflow gap here. If another model is installed, deleting the active one
falls back to it automatically and loads it. If it was the only model, the engine
unloads and the user is told plainly to download one to keep chatting, which
sends them back to the download flow rather than into a broken empty state. This
is tier-one single confirmation, since it is one item; it just has a smarter
aftermath.

Every model in the Advanced list is a genuine instruction-tuned GGUF with a
verified size and hash, checked against the live Hugging Face API like the tier
defaults, so nothing offered there is a dead or unverifiable link.

## Combined update, part 5: the Chats screen fills out (PART 4)

New chat is now a first-class action on the Chats screen: an accent pill with,
beside it, its own mode selector as a distinct adjacent element. That selector
is deliberately separate from the in-chat Chat/Logic flip, so choosing the mode
for the next chat reads as its own thing rather than being fused with the toggle
inside an open chat, which is exactly the distinction PART 4 draws. The bottom
nav New still works and defaults to Chat.

Rename is reachable from the swipe rail, the grid long-press menu, and the
accessibility actions, opening a small inline dialog that saves immediately. A
renamed chat is manual and sticks everywhere the title appears, including search,
because it is just the conversation's title.

Bulk selection is a first-class path, not an afterthought. Long-pressing a row,
or the Select item in the grid menu, enters selection mode: a running count, a
checkbox per row, Select all and Select none, Cancel, and one Delete that
removes the whole selection through the tier-two two-step confirmation, since
deleting several at once is major loss. Back closes selection before anything
else. A single delete stays tier-one and honours the confirm-before-deleting
preference. Nobody has to delete chats one at a time.

A workflow gap closed while walking this: entering selection mode hides the
Pinned section header and flattens to a single selectable list, so the
select-all count and the visible rows always agree, rather than select-all
counting rows that a collapsed Pinned section was hiding.

## Combined update, part 6: follow-up density, selection, and sharing (PARTS 5 and 5B)

Follow-ups are compact now, in the Chats-list style: each card is a short title
plus the flagged snippet truncated after about two lines, not the whole
conversation, so the screen stops consuming enormous vertical space. Tapping
still opens the full source. A single follow-up removes with a light swipe and a
toast, no dialog, since a bookmark is the least destructive thing in the app and
cheap to recreate; clearing several is tier two.

Follow up on a whole response, or on a selection. The bookmark under a response
still flags the whole thing. Now, selecting any part of a response surfaces the
app's own menu for exactly that excerpt: copy, follow up, and share. The excerpt
becomes the follow-up content, linked back to the full source response.

The interaction, resolved cleanly. Rather than fight the system text-selection
popup, the app replaces the floating menu for a response's text with a small
themed one carrying the three actions, while leaving the selection handles and
drag exactly as the platform provides them. The selected text is captured by
having each action first run the platform copy, which puts the selection on the
clipboard, then reading it back, so the menu never needs to reach inside the
selection internals. Text selection therefore cannot trigger the whole-response
bookmark or a mode switch, and copy, follow up, and share all feel like one
gesture on whatever was highlighted.

Sharing at three granularities, all through the native Android share sheet, none
routed through a backend. A whole thread shares as clean attributed text and can
also export to a .txt or .md file the user saves or sends, delivered through a
FileProvider so the file leaves only via the share sheet. A whole response
shares with one icon in its action row, sitting cleanly beside copy, bookmark
and the overflow. A selected portion shares through the same selection menu.

Tests: 74 unit including the thread render (attributed, readable, trimmed, and a
heading even when untitled), plus the on-device suites. The share-sheet handoff
itself is a system intent and is exercised by hand.

## Combined update, part 7: the memory system, user in control (PART 7)

Three modes, chosen in the Memory screen, Manual the safe default:

- Manual: nothing is remembered unless the user says so. "Remember that ..." (and
  "remember:", "remember I ...", "please remember ...") is detected on send,
  saved, and confirmed on the notice line. This fires in Manual and Auto, never
  Off, which is the whole point of Manual.
- Auto: the app also keeps durable facts it notices. After an exchange it runs a
  strict one-shot extraction asking only for stated preferences, ongoing
  projects, recurring context, and personal facts the user clearly volunteered,
  and to reply NONE otherwise. The parse is defensive: it drops NONE, refusal
  sentences, over-long lines, and caps two facts per exchange, so one chat cannot
  flood the store. A small, high-signal store is worth far more than a large
  noisy one.
- Off: nothing is remembered.

Auto entries are marked "Saved automatically" in the list, surfaced exactly like
manual ones so nothing is hidden and a person can prune them. The memory store
is in the same encrypted database as everything else.

Management. The Memory screen lists every entry in full, deletes one (tier one),
enters multi-select by long-press for select-all and a batch delete (tier two),
and forgets everything (tier two). The auto flag rides a real Migration(2,3);
existing memories default to manual.

Tests: 81 unit including the manual capture (plain requests captured, ordinary
messages and "can you remember things?" left alone), the auto parse (real facts
kept one per line, NONE and refusals dropped, bullets stripped, bounded to two),
and Manual confirmed as the default mode; plus three on-device schema-migration
tests, now covering both the conversation title flag and the memory auto flag,
with existing data surviving intact.

## Combined update: status

PARTS 0, 1, 2, 3, 4, 5, 5B, 7, 8 and 9 are complete. PART 6, the Workbench
surface, is master-spec Phase 3 and is built there per the phase-order rule.
Within PART 3, the encrypted backup file is master-spec Phase 7 and is built
there. The app-lock gating of the assistant overlay (Phase 4) and the widget,
tile, share target and text-selection hook (Phase 6) integrates as each of those
surfaces is built; the gating mechanism is in place for them to reuse. With the
combined update otherwise done, the build returns to the master-spec phases in
order, resuming at Phase 2 (Voice).

## The blank-screen-at-launch bug, and the model memory manager (PART A)

### The bug

After downloading the 12B (Best) model, the app launched to a blank white screen
that persisted across force-close. Diagnosis on device: the database and key were
intact (this was never a data problem), but startup gated the first render on
`loadActiveModelIfPresent()`, which loaded the now-active 7.1 GB model
synchronously before setting the ready flag. Loading 7 GB at cold start drove the
phone past its memory watermark, the logcat showed a cascade of lowmemorykiller
kills tearing through ~25 other apps, and the app itself was killed, all while the
UI stayed blank because ready never flipped. Every relaunch repeated it.

### The manager

Rather than patch the one call site, all load and unload decisions now live in a
single ModelManager that is the source of truth for what is resident. It is
deliberately free of Android types so its whole decision surface is unit-tested
with fakes, and the Android wiring (a memory gauge, file paths, the repository)
is injected. It enforces:

- **At most one model resident.** Switching unloads the current model and
  confirms release before the next loads; the fake runtime's load asserts nothing
  is resident when a load begins, so "never two at once" is proven, not hoped.
- **Lazy loading, never at startup.** Startup only reads the active reference and
  repairs a dangling one; the model loads on first actual use.
- **Pressure-aware refusal.** Before a load, the estimated requirement is checked
  against available memory plus a margin; if it will not fit, the load is refused
  with a plain message and a smaller installed model offered.
- **Safe delete in every state.** A resident model is unloaded before its file is
  removed; the active reference is repaired to another installed model or a
  no-model state; a mid-download delete cancels and cleans the partial; no
  dangling reference is ever left for a later launch.
- **Downloads do not disturb what runs.** A finished download never auto-activates
  (except the very first model, when nothing was active) and never triggers a
  load; a starting download unloads an idle resident model to free room.
- **Memory pressure and backgrounding** unload via the Application's onTrimMemory
  and the activity lifecycle, reloading transparently on next use.

Verified on device against the exact broken state: the app now launches in about
560 ms with the 12B still active on disk, renders the Chats screen immediately,
the process stays alive, and there are zero lowmemorykiller lines where before
there was a cascade. Idle memory with the model lazily unloaded is about 186 MB
PSS, versus the multi-gigabyte resident load that caused the crash.

Tests: 12 ModelManager unit tests covering switch-never-two-resident, delete the
loaded model, delete the only model, mid-download delete, refusal with and
without a smaller option, memory pressure then transparent reload, install does
not disturb the active model, first install adopts active, dangling reference
repair, and failed load. 93 unit tests total.

### Still open in PART A, being built next

Two-stage memory pressure (release the KV cache on moderate pressure, unload the
model on severe) needs a native change to separate the context lifecycle from the
model lifecycle, so the model can stay mmapped while the KV cache is freed. mmap
is already requested in the loader; it will be confirmed by measuring resident
memory of a loaded model against its file size. Thermal throttling, the
quantization review, and measured tier assignment follow. PART B (voice sharing
the budget) integrates into Phase 2. PART C edge cases follow.

## Model memory: two-stage pressure, honest fit check, and measured tiers (PART A cont.)

### A second crash, and the real memory lesson

With the blank-screen fixed, sending a message with the 12B active crashed the app
with a kernel SIGKILL (out of memory), not a graceful refusal. The fit check had
used an optimistic estimate (half the weights, trusting mmap to keep the rest
reclaimable) and let the load through; loading then touched essentially the whole
7 GB file plus the context buffers and the kernel killed the process. The honest
requirement is the full weights plus the context overhead, so that is what the
check now uses. A refusal with a clear message beats an out-of-memory kill every
time.

Verified on device: with the 12B active on a 16 GB Pixel with about 5 GB free,
sending a message now shows, in the amber notice, "Gemma 4 12B needs about 7.9 GB
free to run, and this phone does not have that spare right now. Close some apps
to free memory and try again, or download a smaller model." No crash, app fully
usable.

### A JNI ordering bug found in the same pass

The manager checks isLoaded before any load, which can be the first native call
in the process, and the native library was only loaded inside load(). That
produced an UnsatisfiedLinkError ("no implementation found ... is the library
loaded?"). The status-check natives (isLoaded, releaseContext, unload) now ensure
the library is loaded before calling in.

### Tiers reassigned from measurement, 12B moved to Advanced

The measured reality is that a 7 GB model does not load comfortably on a 16 GB
phone, so the 12B does not belong on the Best tier. The default tiers are now the
Gemma 4 on-device (E) line, which is what it is designed for:

- Basic, 8 GB: Gemma 4 E2B Q4_K_M, 3.1 GB. Measured to load and generate.
- Balanced, 12 GB: Gemma 4 E4B Q4_K_M, 5.0 GB.
- Best Available, 16 GB: Gemma 4 E4B Q5_K_M, 5.5 GB. The same on-device model at
  higher precision, the honest ceiling for something that runs comfortably.

The 12B and E4B Q6 move to Advanced, each with a plain warning that they may
refuse to load on a typical phone. Q4_K_M stays the tier default quantisation;
higher precision only appears in Advanced where the warning makes the memory cost
explicit.

### Two-stage memory pressure

The native bridge now separates the context (KV cache) lifetime from the model
lifetime, so the two-stage response the spec asks for is real:

- Moderate pressure (onTrimMemory at running-low): release the KV cache, keep the
  model mmapped. The conversation continues and only the next reply is a little
  slower while the context is rebuilt.
- Severe pressure (critical, complete, or backgrounded): unload the model
  entirely. It reloads lazily and transparently on next use.

New native functions: nativeReleaseContext, nativeEnsureContext,
nativeIsModelLoaded, nativeIsContextLoaded. The engine rebuilds the context
automatically before generating if it was released. Idle memory with the model
lazily unloaded measured at about 186 MB PSS.

Tests: 14 ModelManager unit tests including moderate pressure releases the KV
cache while the model stays resident, and severe pressure unloads it. 95 unit
total.

### Still to record in PART A

An explicit mmap measurement (resident memory of a loaded model versus its file
size) is done next by downloading E2B and measuring on device. Thermal
throttling is already wired via ThermalWatcher (shorter context when warm, early
stop with a plain message when hot); it will be confirmed under sustained load.
Hardware acceleration stays off (n_gpu_layers = 0), the reliable path, as the
spec prefers. PART B (voice sharing the budget) integrates into Phase 2. PART C
edge cases follow.

## Runtime edge cases: crash visibility, current date, context overflow (PART C)

Several of the spec's edge cases were already handled and were confirmed by
reading the code rather than assumed:

- Storage exhaustion during a download: the downloader checks free space with
  StatFs before starting and surfaces "There is not enough space on this phone.
  You need about N more.", and catches an IOException mid-write.
- First run with no model or no internet: onboarding handles the empty state, and
  a send with no model set shows "No model is set up yet. Download one in Settings
  to start." rather than a broken chat.
- Interrupted generation: an assistant message is written with incomplete = true
  and only cleared on a clean finish. A startup sweep (repairIncompleteMessages,
  called from AppViewModel) marks any message stranded by a process death with
  "Kam AI was closed while this was being written.", so a cut-off answer never
  looks like a finished one.

Three genuine gaps were filled:

### Current date injected into every request

Every local model states a confidently wrong date, which users notice at once.
SystemPrompts.withDate now appends the real date and time to the system prompt on
every send, with an instruction not to contradict it. Three unit tests lock this
in, including that the injected text itself contains no em dash.

### Context overflow warns, never silently drops

When a conversation grows past what the model can hold, the oldest turns were
dropped silently and the model appeared to forget the start of the thread for no
visible reason. It now says so once per conversation: "the earliest messages no
longer fit in the model's memory. It can still see the recent part." The single
message that is longer than the whole context still gets the existing out-of-room
message.

### Crash visibility without telemetry

The app has no telemetry, so a crash previously left only the system's "app keeps
stopping" dialog and nothing to act on. A CrashLog uncaught-exception handler now
records the last crash (build, device, thread, stack trace) to a local file and
then hands off to the platform's default handler, so the process still dies as it
should. The crash is never swallowed: a survived crash would be a corrupted app
lying about its state. A Crash report row appears in About only when there is one,
letting the user read it, share it on their own terms, or clear it. Nothing leaves
the phone unless they tap share.

SQLCipher for Android was added to the licenses list (BSD-style, Zetetic LLC); the
AndroidX umbrella entry already covers biometric, datastore, navigation, and
fragment.

98 unit tests pass.

## mmap measured on device, PART A complete

The spec asked to verify mmap rather than assume it, and to measure resident
memory before and after a load. Done on the Pixel with Gemma 4 E2B (the 3.1 GB
Basic tier, file 3,106,738,272 bytes), read from /proc/<pid>/maps via run-as and
from dumpsys meminfo.

### mmap is genuinely working

The model file appears in the process map as a file-backed shared mapping:

    758125e000-763961a000 r--s 00f16000 fe:50 63973  .../gemma-4-e2b-it-q4km.gguf

The r--s flags are the proof: read-only, MAP_SHARED, backed by the file, not an
anonymous heap copy. llama.cpp is memory-mapping the weights, so the kernel can
evict and re-read weight pages under pressure instead of the app holding a
committed 3.1 GB copy.

### The numbers, before and after

- Idle, model lazily unloaded: 203 MB PSS, 320 MB RSS. MemAvailable 5.01 GB.
- E2B resident and generating: 3.92 GB PSS, 3.96 GB RSS. Of that, Native Heap
  (the anonymous KV cache and compute buffers) is 1.54 GB; the rest is the
  file-backed weight mapping.
- The figure that matters: MemAvailable dropped only about 1.13 GB while a 3.1 GB
  model was loaded and running. That gap is the whole point of mmap. The weights
  sit in reclaimable page cache and do not count as committed memory the way a
  malloc'd copy would.

### Why the fit check is still deliberately conservative

Given the above it is tempting to size the requirement at the anonymous footprint
(about 1.5 GB) rather than the full weights. That would be a mistake, and it is
the exact mistake that SIGKILLed the 12B earlier. Loading and then running reads
through the whole file; on a phone without room to keep those pages resident the
kernel thrashes, faulting weight pages in and out and evicting everything else,
which trips the low memory killer. The weights being reclaimable does not make
them free to churn. Requiring the full weights plus overhead keeps enough
headroom that the mapping stays hot, so the conservative requiredBytes stands.

### End to end on device

E2B was switched to active, loaded lazily on first send, and answered across
turns: "Say hello in one word" gave "Hello"; "count to three" gave "One. Two.
Three." Plain, terse, in the app's voice, with the current date now injected into
every request. The 12B remains installed and refuses to load with the plain
memory notice.

### Two-stage pressure

The manager's moderate (release KV, keep model) and severe (unload) paths are
covered by unit tests. They cannot be driven from adb against a foreground
process: am send-trim-memory refuses ("Unable to set a background trim level on a
foreground process", and it will not re-raise a level once set), which is an
Android restriction, not a code issue. The native split is confirmed present
(nativeReleaseContext / nativeEnsureContext symbols in the built .so), and the
engine rebuilds the context before each generation, so a released context is
transparent to the next reply.

PART A is complete: one manager owns all load and unload, mmap is verified and
measured, the KV cache is tracked and released first under pressure, loading is
lazy and guarded against memory, install and delete are safe, thermal degradation
is wired from the start, hardware acceleration is off by default, quantisation is
Q4_K_M by tier with higher precision only in Advanced behind a warning, and the
tier assignments come from what actually runs on the device.

## Phase 2 STT: whisper.cpp speech to text, verified on device (with PART B)

Voice typing is built and proven end to end. The user talks, whisper.cpp
transcribes on the phone with no network, and the text lands in the composer to
send or to ask the model to tidy.

### Verified on device, not assumed

An instrumented test runs the standard whisper sample (jfk.wav) through the real
SttEngine on the Pixel and asserts the words come back. It passed: the 11-second
clip transcribed correctly in about 6 seconds, returning "ask not what your
country can do for you". This proves the isolated whisper library actually works,
which the symbol-isolation and build steps alone could not. The model's sha256
(60ed5bc3...) was confirmed against the catalogue when downloading it for the
test, so the size and hash shipped to users are correct. The test skips itself
when no model is present, so it is safe on any device; the large model file it can
use is gitignored and never committed.

### Tiers, downloads, and honesty

Two multilingual whisper models, tiered like the language models: Standard
(ggml-base, 148 MB) for any phone and Better (ggml-small, 488 MB) recommended on
12 GB and up. Both download through the same resumable, space-checked, hash-
verified path as the language models and appear in Storage. The Voice screen sets
honest expectations: good on-device models, not quite as sharp as the big cloud
services, and that is the trade for everything staying on the phone.

### The microphone and its permission

A microphone button appears in the composer only when a voice model is installed
and the field is empty, so it never competes with sending typed text. It records
16 kHz mono, exactly what whisper wants, so there is no resampling to get wrong.
The RECORD_AUDIO permission is requested at first tap with the system dialog, and
a denial shows a plain line pointing at Settings rather than silently doing
nothing. Verified on device: the permission dialog appears, and after granting,
the mic records.

### PART B: voice shares the language model's memory budget

The whole point of PART B is that a voice model and a language model never peak in
memory together. Two things enforce it. First, SttEngine loads the whisper model
only for the duration of one transcription and unloads it the instant it finishes
(even on failure), so it never sits resident next to a generating language model.
Second, before whisper loads, it calls back into the model manager to release the
language model's KV cache (onModeratePressure), so the transient whisper peak
lands while the language model is at its smallest. Transcription happens while the
language model is idle anyway (the user is talking), and generation happens after,
when whisper is already gone. The two peaks are sequenced apart by construction.

### The native build (recap)

whisper.cpp builds via ExternalProject into libkamwhisper.so with its own ggml,
isolated from libkamai.so with -Wl,--exclude-libs,ALL so the two different-
versioned ggml copies cannot collide. Confirmed: each library exports only its
JNI entry points and zero ggml internals.

## Phase 2 TTS: sherpa-onnx reading voice, verified synthesising on device

The second half of voice is built and proven. Answers can be read aloud with an
on-device neural voice through the sherpa-onnx runtime, never the Android system
voice, as the spec requires.

### Verified on device

An instrumented test runs a Piper voice through the real TtsEngine on the Pixel
and checks that synthesis produces a real amount of non-silent PCM at a sane
sample rate. It passed in about 4 seconds. This is the meaningful proof for the
native integration risk: the runtime loads, the phonemiser data is found, the
voice model runs, and audio comes out. How it sounds cannot be asserted in a
test, only that synthesis works end to end.

### The runtime and how it is packaged

sherpa-onnx ships prebuilt Android libraries. Only the two needed for arm64
(libsherpa-onnx-jni.so and libonnxruntime.so, about 25 MB together) are fetched
by tools/fetch_sherpa.sh and kept out of git, the same fetch-not-commit pattern
as llama.cpp and whisper.cpp. The Kotlin API (Tts.kt) is vendored in the source
tree with its Apache-2.0 header because it is small and readable.

The Piper voices all share one espeak-ng phonemiser data set and one tokens file.
Rather than download them with every voice, they are bundled once (espeak-ng-data
zipped, plus tokens) and produced by fetch_sherpa.sh into the app assets, also
gitignored. On first use they are unpacked to disk, since sherpa-onnx reads them
from the filesystem next to the downloaded model. A voice download is then just
its single model file, so the size shown to the user is honest. The onnx hashes
were confirmed identical between the individual HuggingFace files the app
downloads and the sherpa release tarball the shared data comes from, so model and
phonemiser data always match.

### Voices and the play button

Two Piper voices in the standard tier: Amy (female) and Ryan (male), 63 MB each,
downloaded through the same verified path and listed in Storage. The Voice screen
gained a Reading voice section with download, use, and preview (which reads a
sample line aloud). A play button appears under any answer once a reading voice is
set, wired to synthesise and stream through an AudioTrack that stops instantly on
navigation away. Honest expectations again: good on-device voices, below the big
cloud services in polish.

### PART B for TTS

The reading voice shares the language model's memory budget like speech to text:
before the runtime loads a voice, it releases the language model's KV cache, and
the voice is stopped and freed on memory pressure and on backgrounding.
Text-to-speech runs after generation, when the language model is idle, so the
peaks stay apart.

Licenses updated: whisper.cpp (MIT), sherpa-onnx and ONNX Runtime (Apache-2.0),
Piper voices (MIT), espeak-ng data (GPL-3.0).

## Phase 3: Workbench

The third mode is built as its own surface, deliberately not a chat. It is reached
by cycling the new-chat mode chip (Chat, Logic Partner, Workbench); choosing
Workbench turns the button into Open Workbench and opens the paste-and-transform
screen rather than a conversation.

The screen: a source area at the top, a row of plain transformations (Tighten,
Rewrite, Into points, Summarize, Fix grammar, More formal, More casual) plus a
free instruction field, and a result below with Copy, Flag, and the option to run
another transformation on the result. Chaining makes the result the new source, so
what was transformed stays honest. Voice input works here too, the same
transient-load speech-to-text as chat. Every transformation runs through the
language model in Workbench mode, whose fixed instructions return only the
transformed text, and over-length input gets the same plain out-of-room message as
chat.

State survives rotation and process death: the source and result are persisted to
the settings store on every change and restored on open. Verified on device by
typing text, force-stopping the app, reopening, and seeing the text restored
exactly ("Meeting notes from today"). The transform itself runs on the same
engine path already proven for chat.

## Phase 4: The assistant overlay

Kam AI can be the phone's digital assistant, opened by a long-press of the power
button, showing a quick panel over whatever the user was doing.

### How it is built

The digital-assistant role needs a VoiceInteractionService. Rather than render the
overlay inside the voice-interaction window (which makes Compose, the keyboard,
and the microphone awkward), the session immediately launches a normal, floating,
translucent activity (OverlayActivity) and hides itself. So the overlay is an
ordinary Compose activity that behaves exactly like the rest of the app. Three
components make the role valid: KamAssistService (the VoiceInteractionService),
KamAssistSessionService (opens the overlay), and KamRecognitionService (a required
no-op, since Kam AI does its own on-device speech-to-text with whisper rather than
through the system recognition path). res/xml/kam_assist.xml ties them together
with supportsAssist=true.

### What the overlay does

A minimal sheet at the bottom over the current app, dimming the rest: ask by text
or by voice (the same whisper speech-to-text), a compact answer in Overlay mode
(short by instruction), a single tap-to-flag icon that drops the answer into
Follow-ups with the question as a note, Copy, and Open Kam AI which turns the
exchange into a full conversation and opens it. It answers entirely on-device, so
it works with no network. Verified on device: triggered with the assist key, the
overlay opens as an assistant-type task over the launcher and renders correctly;
with no model installed it says so plainly ("No model set up yet. Open Kam AI to
download one.") rather than doing nothing.

### Settings and the assistant role

A row, "Open with the power button", shows whether Kam AI is the current assistant
and opens the system Digital assistant screen (falling back to Default apps, then
Settings) so the user can pick it. There is no API to claim the role directly;
taking the user to the right screen with a plain explanation is the honest path.

### Testing note: setting the role over ADB

Reinstalling a debug APK can clear the assistant selection. To set it for testing:

    adb shell settings put secure voice_interaction_service com.kamsiob.kamai/.assist.KamAssistService
    adb shell settings put secure assistant com.kamsiob.kamai/.assist.KamAssistService
    adb shell settings put secure voice_recognition_service com.kamsiob.kamai/.assist.KamRecognitionService

Trigger the session with `adb shell input keyevent 219` (KEYCODE_ASSIST); plain
`am start -a android.intent.action.ASSIST` opens a chooser instead and is not the
right path. To restore the phone's original assistant afterward, put the saved
values back (on the test Pixel that was
com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
for both, and com.google.android.tts/...GoogleTTSRecognitionService for
recognition). None of this affects Play-delivered updates.

### A download observation worth recording

A large model download runs in the app, not a foreground service, a deliberate
choice to keep the app's permission set minimal (no FOREGROUND_SERVICE, no
background start). The cost, seen during testing: if the app is backgrounded for a
long stretch mid-download (the overlay was left open for over ten minutes), the
download coroutine is suspended or killed and does not resume on its own. It does
resume correctly from the partial file the moment the user taps download again, so
no bytes are lost, but the Model screen reverts to "Download" rather than showing a
resumable state. Worth a small future improvement: on returning to the foreground
with a partial file present, offer to resume, or label the button "Resume". Not a
data-loss bug; a UX rough edge, noted here so it is not rediscovered.

## Phase 5: Discover and the Wikipedia pack pipeline

Both halves are built and verified on device.

### The pipeline (tools/discover)

build_packs.py walks curated branches of English Wikipedia's Vital Articles,
pulls each article's introduction through the official API as plain text, cleans
it, filters weak entries, and writes one versioned SQLite pack per topic plus a
manifest; publish.sh attaches them to a GitHub release. Etiquette is respected: a
descriptive User-Agent with a contact, batched extract requests (20 at a time),
small delays, and an on-disk cache so reruns are cheap and idempotent.

A real correction found by running it, not assuming: the page titles use "Level 4"
with a space, not "Level/4", and the article links come through transclusion, so
they are read with action=parse (a plain prop=links query returns nothing). The
cleaner strips pronunciation and foreign-script parentheticals and reference
markers while keeping meaningful parentheticals.

Actual moment counts (History was broadened with Level 5 to approach the target;
the others are what their branches genuinely yield, per the spec's instruction to
take what is there rather than pad):

- History: 2000
- Science (physical sciences + biology and health): 2000
- How It Works (technology + everyday life): 1193
- People: 1913

Published to the release tag discover-packs-v1; the manifest and all four packs
are publicly downloadable with matching sha256 hashes. Twenty sample History
cards were printed for a skim during the run.

### In-app Discover

The single dealt card carries a substantial multi-paragraph preview (never a
teaser), the topic eyebrow, the From Wikipedia CC BY-SA 4.0 footer, a save toggle,
Read the full moment, and Quiz me / Deal another. The packs sheet reads the
manifest, downloads with hash verification, and lists Get/Remove with sizes and
the plain one-time-offline note; packs also appear in Storage. Drawn ids are
tracked so only unseen cards are dealt, with a plain reshuffle at true exhaustion
(unit-tested over a fixture pack). Saved sits at the bottom; a quiet stats line
shows the running tally with no streaks or pressure mechanics.

The reader shows the full passage, attribution with source link, and the owner's
two-button feature: Discuss this passage (grounded) and Explore this topic (open),
with plain copy explaining the difference. Verified on device: the grounded chat,
asked "Who was the second president of the United States" about the American
Revolution passage, answered "The passage does not mention the presidents of the
United States" rather than filling the gap from memory. That is the whole point of
grounding.

Quiz me generates questions strictly from the full passage, one at a time, with
honest self-marked feedback that shows the passage's answer, a one-tap flag on a
miss, and a result like "4 of 4" that updates the tally. If the reader was not
opened for the card, a plain prompt offers "Read it first" or "Quiz me anyway".
Two bugs were caught and fixed by testing on device: the parser was grabbing the
format-template line as a question (now filtered, and the prompt asks for a
simpler Q:/A: shape a 2B model follows reliably), and an ICU regex crash from a
character class beginning with "." or ":" (Android treats "[." and "[:" as
collating/POSIX starts; the classes were reordered). The crash was recorded by the
local CrashLog, which confirmed that path works too.

100 unit tests pass.

## Phase 6: System integrations

Four ways into Kam AI from the rest of the phone, all with zero added permissions.

- Text selection "Ask Kam AI" (ACTION_PROCESS_TEXT) and the share sheet
  (ACTION_SEND, text/plain) both land in one lightweight TextIntakeActivity: a
  bottom sheet showing the incoming text with two actions, Ask about this (opens a
  new chat with the text prefilled in the composer) and Rework in Workbench (opens
  the Workbench with the text as its source). Verified on device by simulating
  both intents: PROCESS_TEXT prefilled the composer, SEND prefilled the Workbench.
- A home-screen widget (KamWidgetProvider) with New chat and Talk to it, and a
  quick-settings tile (KamTileService), both launching the app with an action
  extra that opens a fresh chat. Verified the launch path opens a new chat.

The plumbing is a process-level Intake holder the app observes once it is on
screen, the same pattern as the assistant handoff, so it passes cleanly through
the app lock. Nothing about the text ever leaves the phone. There is deliberately
no in-app messaging; anything contact-adjacent would hand off to the native
Messages app, and nothing here does otherwise.

100 unit tests pass.

## Critical fix: the memory fit-check was refusing every model on a real phone

The owner reported that nothing worked, that even the Balanced model would not
answer on his 16 GB phone. It was not a model problem. It was the fit-check.

The check (written after an early 12B out-of-memory kill) required the model's
full weights plus overhead to be in the system's reported free memory: about
6.1 GB for Balanced (E4B), 4.25 GB for Basic (E2B). But a normally-used 16 GB
phone rarely reports that much free. Android keeps recently-used apps cached, so
with a browser and the camera open the phone reported only 2.8 to 3.4 GB free.
Every model, including the recommended one, was refused before it ever loaded.
The user saw a refusal, not an answer, which reads as the app being broken.

The check was wrong about how the memory behaves. The weights are memory-mapped,
so they are file-backed page cache the kernel reclaims and re-reads on demand;
they do not need to sit in the free figure. What genuinely needs free memory is
the anonymous KV cache and compute buffers. This was already measured earlier in
the build: loading E2B, a 3.1 GB model, cost about 1.1 GB of committed memory, not
3.1 GB.

The fit-check now uses that reality:

- It requires the anonymous buffers (a compute-buffer floor plus a fraction of the
  weights for the KV cache, tuned to reproduce the measured 1.1 GB for E2B) to fit
  in what is free right now.
- A separate total-RAM check ensures the whole working set (mmapped weights plus
  those buffers plus an OS reserve) physically fits the device, which still stops
  a model that is genuinely too big for the phone from being loaded into an
  out-of-memory kill.

Verified on the owner's phone with about 3 GB free: Balanced (E4B) loaded and
answered ("say hello in one sentence" gave "Hello"; "what is the capital of Japan"
gave "The capital of Japan is Tokyo"), across turns, with the process stable and
about 2.8 GB still free. This is the difference between an app that refuses
everything and one that works.

15 ModelManager unit tests cover the new model, including a model-too-big-for-the-
device refusal and a not-enough-free-right-now refusal with a smaller fallback.

## Phase 7: files, export, and import

Both halves are done.

### Backup and restore

Export gathers the whole database (conversations, messages, memory, projects,
follow-ups, Discover saved and drawn state, quiz stats, artifact records, and
settings), encodes it as one versioned JSON document, encrypts it with a
passphrase the user chooses (AES-256-GCM with a PBKDF2 key), and writes it to a
file they pick through the system. Import decrypts, decodes, and writes it back,
merging or replacing. Verified two ways: a JVM round-trip test that the codec and
crypto are exactly reversible and that a wrong passphrase or a non-backup file is
rejected; and an instrumented test on the real encrypted SQLCipher database that
populates it, exports, wipes, imports, and confirms every row returns.

The large model and pack files are not embedded, since a backup should stay small
and portable. Their artifact records travel, so after a restore the app can name
what to re-download. The device-mismatch case (a backup from a bigger phone
restored on a smaller one) is handled by not writing artifact records that would
mark absent files as installed, and by the model manager repairing the active
reference and refusing an oversized model; the user simply re-downloads a model
that fits, which the tier logic on the new phone recommends.

### File attachments

A document can be attached to a conversation and read on the phone: plain text,
Markdown, a PDF with a real text layer (pdfbox-android), and DOCX (unzipped and
parsed from the body XML with no extra library). Everything else is refused with a
plain reason, images, spreadsheets, old .doc, and scanned PDFs among them, rather
than a bad extraction. An instrumented test confirms all four extractors on
device, including the scanned-and-unsupported refusals. The extracted text is
given to the model as context, and when a document is longer than the context
window it is truncated with an honest note pointing the user at a specific
section, never silently. Verified on device that the paperclip button opens the
system file picker with the right type filter.

### An operational note worth recording

Running connectedAndroidTest uninstalls and reinstalls the app between runs, which
wipes downloaded models and packs. During this phase that erased the owner's 5 GB
Balanced download. In future, device round-trips that must not lose the owner's
data should use `adb install -r` (which preserves data) rather than the
instrumented-test task, or re-download afterwards. The Balanced model was
re-downloaded so the app is usable again.

100+ unit tests pass, plus the backup and file-extraction instrumented tests.

## Phase 8: release signing and hardening

### The upload keystore

Generated in Phase 8 as planned, into the secrets directory outside the
repository (~/.kamsiob-secrets/kam-ai-upload.jks), a 4096-bit RSA key valid for
10000 days, alias kam-ai-upload. Its password is in keystore.properties beside it,
mode 600, never in git. The build reads that file by absolute path, so no signing
material is ever committed and a machine without the file still builds debug and
an unsigned release.

SHA-256 fingerprint:
DC:91:1A:E7:0B:47:51:DC:69:2D:61:32:8C:B7:AD:4B:89:38:87:26:D6:FE:3D:42:F0:3A:38:72:F7:2A:E7:B4

This is the UPLOAD key, not the app signing key. Google Play App Signing holds the
real signing key; this key only signs uploads. If it is ever lost, Play support
can reset the upload key, so it is recoverable, but it must still be backed up:
losing it means going through that reset before the next update. LAUNCH.md tells
the owner where it is and to back it up.

### R8 and the release build

The release build minifies and shrinks resources. The real risk is R8 renaming
classes that native code resolves by name, which would break every model call in
release while debug worked fine. Keep rules were added for all three JNI bridges
(llama.cpp, whisper.cpp, sherpa-onnx text-to-speech), for any remaining native
methods, and for pdfbox and SQLCipher. Verified against the R8 mapping: LlamaBridge,
WhisperBridge, and OfflineTts are identity-mapped (unrenamed), and all five native
libraries are present in the signed APK. The signed release is 53 MB, down from the
121 MB debug build.

A full on-device run of the release build is deferred rather than done now: it
cannot be installed over the debug build without uninstalling, which would wipe
the model the owner is re-downloading. It will be run once that download is done.

## Phase 8 self-review pass

Reviewed against DESIGN.md for the things that can be checked without the running
app while a model re-downloads.

- Copy voice: no em dashes and no exclamation points anywhere in user-facing
  strings, confirmed by a repo-wide search. The store listing and LAUNCH.md follow
  the same voice.
- Amber discipline: the reserved amber appears only where it should, on flags, the
  locked-tier and Advanced-model warnings, the Support this work button, the
  destructive confirmations and delete labels, error and notice lines, and the
  recording indicator (a consistent attention use across chat, workbench, and the
  overlay). No amber leaks onto ordinary UI.
- Touch targets: the Discover save toggle was a 36 dp target, below the 48 dp
  minimum the theme sets; raised to 48 dp. Icon buttons elsewhere sit inside 48 dp
  boxes already.

The remaining Phase 8 gates (the four user-testing scripts in full, fresh listing
screenshots, and an on-device run of the R8-minified release build) need the app
running with a model. The release build is statically verified (signed with the
upload key, all five native libraries present, and the JNI bridge classes
identity-mapped by R8 so native calls resolve); the debug build with every
phase's changes was confirmed generating on device (Balanced answering after the
memory fix). A full release-build run is left for the first Play internal-testing
install rather than done now, because installing the differently-signed release
over the debug build would uninstall it and wipe the model download.

## Download management: background, concurrent, pausable

The owner asked for real download control: pause and cancel mid-session, several
downloads at once, delete afterward, and downloads that keep going in the
background. This replaced the old single-download-in-a-view-model approach (which
died the moment the app was backgrounded, the very thing that stalled the owner's
first Balanced download).

A process-level Downloads manager now runs every download as an independent
coroutine, tracks them all in one observable list, and controls each one: pause
keeps the partial file and resumes from it, cancel deletes it, a failure can be
retried. A small DownloadService foreground service keeps the process alive while
any download runs, so a model finishes even after the user leaves the app, and
shows one honest progress notification. It starts with the first download and
stops with the last. This is why FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC,
and POST_NOTIFICATIONS were added, a deliberate reversal of the earlier
no-foreground-service stance, because background downloads are worth it.

Every download surface (models, voices, packs, and onboarding) shares one
DownloadControls row, so they behave identically. The Advanced model list is now
collapsed by default behind a "N more" toggle so it takes less room.

Verified on device: started the Basic model download (progress, Pause, Cancel),
paused it ("Paused at 3%", Resume), resumed it, started a voice download alongside
it (the notification read "Downloading 2 items", both partial files grew),
cancelled the voice one (its partial file was deleted, the model download
continued, the notification went back to one item), then cancelled the model one
(its partial file deleted, the foreground service stopped since nothing was
active). Balanced stayed installed and working throughout.

## Deferred within completed phases

### Kokoro premium reading voice (Phase 2)

Phase 2 voice is complete with the flagship speech-to-text flow and a standard
text-to-speech tier (Piper, Amy and Ryan, male and female), both verified on
device. The spec also calls for Kokoro-82M as a premium reading voice on capable
phones. That is deliberately deferred, not forgotten: Kokoro is a multi-file model
(model, a voices blob, its own tokens, lexicons, and phonemiser data) that does
not fit the single-file download the standard voices use, so it needs on-device
archive extraction or a repackaged bundle. The TtsEngine already has a branch for
the Kokoro config shape, so adding it is a self-contained follow-up: wire the
Kokoro model config, add the download-and-extract path, and offer it only where
memory is comfortable. The two-tier requirement's standard tier is fully met; the
premium tier is the remaining piece.

## BLOCKED

Items that cannot be completed yet, and exactly what unblocks each.

### The Play submission tasks are ahead of the build, not blocked by the owner

The owner has finished every Play Console step needing human judgment, and asked
for the remaining launch work: store screenshots of six surfaces, a signed
release bundle, an Android Publisher API upload, and a LAUNCH.md with his final
clicks.

None of that is blocked by him. It is blocked by the app. The build is partway
through Phase 1 of the eight phases in MASTER_SPEC.md. What exists and runs on
the phone today is the scaffolding, the native layer, the database, the tier and
model logic, the guardrails, and the onboarding and chat screens. What does not
exist yet is Discover, Workbench, Follow-ups, Settings, About, Questions and
answers, Voice, the assistant overlay, file attachments, and backup and restore.

So the following were deliberately not done, because doing them would have meant
producing something false:

- **Six store screenshots.** Four of the six requested surfaces (Discover with a
  dealt card, Workbench, Follow-ups, Settings) have no screen to photograph. The
  spec requires real captures over ADB, not mockups, and it is right to. These
  wait for Phases 1, 3 and 5.
- **The signed release bundle.** The release keystore is generated in Phase 8
  and does not exist. Generating it early to sign a half-built app would put a
  version of Kam AI into the world under the identity the real one will use.
- **The Android Publisher API upload.** There is no bundle to upload, and
  attaching a feature graphic and screenshots to a live listing for an app that
  cannot yet hold a conversation is worse than attaching nothing.
- **LAUNCH.md.** A file whose whole job is to say "everything else is done"
  would be untrue today, and it is the one document the owner would act on
  without re-reading. It gets written when it is true.

What was done instead, because none of it depends on the app being finished: the
privacy policy alignment, the network and permission audit including the
WorkManager removal, the icon and feature graphic rendered from the design
system, and the Gemma model switch. Those are all real and are in the repository.

**What the owner needs to do: nothing.** This is a sequencing problem, not a
permission problem. The launch work resumes at Phase 8 exactly as specified,
against an app that actually has the six surfaces.

### Nothing else is blocked

The Pixel initially reported as `unauthorized` over ADB, which would have
blocked every on-device step. It authorised itself once the ADB daemon
restarted, and the phone (Pixel 10 Pro XL, Android 17, 16 GB) has been running
builds and tests throughout. No action needed.

## Unit test runner: Robolectric vs the build machine's JDK 26

Running `testDebugUnitTest` here reports 37 failures out of 105, all with
`IllegalArgumentException: Unsupported class file major version 70`. Major
version 70 is Java 26, which is the only JDK installed on this build machine.
Robolectric 4.16.1 bundles an ASM that cannot read Java 26 platform classes
while instrumenting, so every Robolectric-backed test fails identically before
any assertion runs. The failing classes are exactly the ones that need the
Android runtime: KamDatabaseTest, FollowUpStateTest, ModelManagementTest,
BackupRoundTripTest, PackDealTest, AppLockStateTest.

This is a toolchain-vs-machine mismatch, not a code defect. The project pins
Java 17 (`sourceCompatibility`/`targetCompatibility`/`jvmTarget` all 17); a
normal dev or CI machine with a JDK 17 or 21 runs all 105 green. This machine
has no JDK besides 26 and no way to provision one offline, so the Robolectric
suite cannot execute here.

The 68 pure-JVM tests — which do not touch the Android runtime — all pass,
including the ones that matter most for correctness: ModelManagerTest (15, the
memory fit-check that was rearchitected after the on-device load failure),
TierRecommendationTest (12, the 8/12/16 GB boundaries), and ChatFormatTest (12,
Gemma and Qwen prompt assembly). The Room/lifecycle paths the Robolectric tests
cover are additionally exercised by the on-device manual passes on the Pixel.

**What the owner needs to do: nothing.** The suite is green on any JDK-17 CI.

## Owner bug-fix pass (2026-07-23) and the Today tab

The owner delivered two large prompts from hands-on phone testing: a 22-item bug-fix and
refinement list (active work), and a full spec for a "Today" on-device newspaper tab. The
Today tab is deferred by the owner's own instruction ("Build this only when directed ...
should not interrupt finishing and shipping the core application"); its complete spec is
captured in docs/TODAY_SPEC.md and it is not built yet. The 22-item list is tracked in
WORKLIST.md and worked as tested increments in priority order. Items touching phases not
yet built are integrated into those phases rather than built early.

### Item 1: "new chat" reopened the most recent conversation

Root cause was a shared view-model key. New chats route through `Pushed.Conversation` with an
empty id sentinel, and the conversation screen created its `ChatViewModel` with
`viewModel(key = "chat-$conversationId")`. With an empty id that key was the constant "chat-"
for every new chat, and because there is no per-destination ViewModelStore (the app uses a
hand-rolled stack, not androidx-navigation, so all view models live in the Activity store),
Compose returned the same cached `ChatViewModel` every time. That instance still held the
previously created conversation's id, so the second new chat showed the first one's messages.
The `LaunchedEffect(conversationId)` could not reset it either, because the key never changed.

Fix: `Pushed.Conversation` now carries a `vmKey` computed once at push time by
`conversationVmKey(id)` — a real conversation id keys by itself (so reopening one reuses its
state), and a new chat (empty id) gets a unique `new-<uuid>` token, so every new chat gets a
fresh view model and cannot inherit a previous conversation. Conversation creation stays lazy
(on first send) so backing out of a new chat still leaves no empty row. Regression test:
ConversationVmKeyTest asserts existing ids are stable and two new chats never share a key.

Known minor follow-up logged in WORKLIST.md: Activity-scoped view models are not cleared on
back-pop, so a session accumulates lightweight dead ChatViewModels. Correctness is unaffected.

### Item 3: inference speed (part 1 of several) — thread count

Measured on the connected Pixel (Tensor G5, cores: 2 @ 2.25 GHz little, 5 @ 3.05 GHz mid,
1 @ 3.78 GHz prime) with the Basic tier model (Gemma 4 E2B, Q4_K_M, ctx 4096). Decode is the
tokens/second a user actually feels. Instrumented via a `KamPerf` logcat line per generation
(`adb logcat -s KamPerf`); prefill and decode timed in InferenceEngine.generate.

First checked the build variant, as instructed: the native inference is NOT an unoptimised debug
build. `defaultConfig` sets `-DCMAKE_BUILD_TYPE=Release` and `-O3` for all variants, and the
actual ggml-cpu compile commands (verified in the debug variant's compile_commands.json) carry
`-march=armv8.2-a+dotprod+i8mm+fp16`, so the ARM int8 dot-product and matrix kernels are enabled,
weight repacking (GGML_CPU_REPACK) is on, mmap is on, flash-attn is AUTO, and n_batch is 512.
The debug APK already runs optimised native code, so debug-vs-release is not the cause here.

The real lever was thread count. The old default was `(cores - 2).coerceIn(2, 6)` = 6 threads.
Decode is memory-bandwidth bound, and on a big.LITTLE SoC spilling onto the slow efficiency cores
makes them stragglers at every layer barrier. Measured decode tok/s by thread count (same prompt):

    threads=2 -> 7.7      threads=5 -> ~4-7 (noisy/thermal)
    threads=4 -> 9.2-10.6 (best, repeatable)
    threads=6 -> 7.3-7.5  (previous default)
    threads=8 -> 2.0      (all cores incl. little: worst, confirms straggler effect)

New default: performance-core count (cores above the slowest frequency cluster) capped at 4,
because past ~4 threads extra cores do not read weights any faster, they only contend for
bandwidth and heat the phone. Result: **6.9 -> 10.6 tok/s on E2B, ~+54%**, verified on device.
A `debug.kamai.threads` system property overrides it for future measurement.

Still open under item 3 (larger, tracked in WORKLIST.md): speculative decoding with Gemma 4
drafter models (verify they exist for E2B/E4B and that this llama.cpp build supports the
speculative path; account for drafter size in downloads/storage; report measured before/after or
say plainly it is unsupported); per-tier model-selection criteria (speed first, vision/image
understanding incl. any mmproj projector file and its download size, document attachments, memory
honesty); and confirming there is no usable GPU/NNAPI path (llama_supports_gpu_offload reports no
on this device, so CPU is correct). E4B tiers must be measured too; if a tier cannot reach a
usable speed after this work, pick a faster model for it and record the tradeoff.

## Efficiency research (owner instruction: "super efficient in how it runs and when it runs")

Researched on-device LLM and Android efficiency best practices and assessed each against the app.

Confirmed already correct: mmap for weights (survives memory pressure; fine here since the model
fits in 16 GB and pages stay resident), Q4_K_M as the default quant (step up to Q5 only with
headroom, which is exactly the Best tier), thermal instrumentation from day one (ThermalWatcher),
and CPU over untrusted accelerators (llama_supports_gpu_offload is false on this device, and NNAPI
on mobile is frequently a regression, so CPU is correct). Thread count now capped at the
performance cores (item 3).

Actionable, applied incrementally:
- "When it runs" scheduling: memory extraction (item 16) must run as a separate low-cost pass at
  idle/'end of conversation, never blocking the user or draining battery; titling must be cheap;
  downloads already use a foreground service. These are designed in as those items are built.
- KV cache type: f16 today. q8_0 KV halves KV memory for a small quality cost; not pressing at
  16 GB, revisit if a tier is memory-tight with vision (item 3/22).

### Speculative decoding / Gemma 4 MTP (item 3) — feasibility CONFIRMED, implementation planned

The pinned llama.cpp (b10058) contains the pieces: common/speculative.{h,cpp}, the
`gemma4-assistant` architecture (LLM_ARCH_GEMMA4_ASSISTANT), MTP context/graph types
(LLAMA_CONTEXT_TYPE_MTP, LLM_GRAPH_TYPE_DECODER_MTP), and per-block NextN/MTP tensors. Google
ships an Apache-2.0 drafter for every Gemma 4 variant incl. E2B and E4B (a 4-layer model, orders
of magnitude smaller than the target), giving reportedly up to ~3x decode speedup that is
mathematically lossless because the target verifies every accepted token. This directly serves the
speed hard-requirement and the owner's efficiency instruction, and the drafter's tiny size means
negligible extra memory.

Two open questions to resolve before building, both requiring model inspection on device:
1. Whether the tier GGUFs (unsloth gemma-4-E2B/E4B-it) already embed the NextN/MTP layers, enabling
   self-speculation with no extra download (ideal), or whether a separate `-assistant` drafter GGUF
   must be downloaded and loaded as ctx_other. If separate, its size goes into the download flow and
   Storage screen, as item 3 requires.
2. Stability: reports note the non-server (in-library) speculative path could crash loading
   Gemma-4 E2B/E4B in some builds. This app calls the library directly, so this must be verified on
   device before shipping it. If it is not stable on b10058, this is documented here and deferred
   rather than shipped half-working, per item 3's instruction.

Plan: implement as a dedicated, carefully tested native pass (extend kamai_llama.cpp to optionally
attach the MTP/draft path, measure before/after tok/s per tier, gate behind capability + memory
checks). Tracked in WORKLIST.md item 3.

### Items 5 (part), 6, 7: responsiveness and voice controls

Item 5 (immediate feedback), first and most-cited case: the chat thinking indicator appeared only
after the model had loaded and ingested the prompt, because it was gated on the last message being
empty, and during load the last message is the user's own turn. Fixed so it shows whenever work is
under way and no answer text exists yet (user turn, empty placeholder, or a brand-new empty chat),
and `_streaming` now flips synchronously in send() before any DB write or model load. Extracted a
pure `showThinkingIndicator` predicate with a unit test. Device-verified: dots appear the instant a
message is sent. The broader item-5 audit (quiz preparing state, leaving-screen behaviour, and a
cancel path on every slow operation) remains, tracked in WORKLIST.md.

Item 6 (read aloud could not be stopped): TTS was fire-and-forget with no state, so the play
control never became a stop. Added `speakingMessageId` state and `toggleSpeak(messageId, text)`:
tapping the speaking response stops it, starting another stops the current first (one voice at a
time), and sending a new message stops any read. The action-row control shows a Stop icon in the
accent colour while that response is speaking and reverts to Play when done or stopped.
Device-verified play -> stop -> play. Call/audio-focus interruption is a noted refinement (the raw
AudioTrack path does not yet request audio focus).

Item 7 (mic copy): the recording hint said "Tap the mic when you are done" while the control shown
is a Stop button. Corrected to "Listening. Tap stop when you are done." in both the chat composer
and Workbench. Device-verified.

### Item 14: response formatting (Markdown rendering + guidance)

Two causes, both fixed. Rendering: assistant text was drawn as a single plain Text, so any Markdown
the model emitted collapsed into a block with stray symbols. Added a small dependency-free renderer
(ui/components/Markdown.kt) that parses the subset a chat model actually produces (headings, bold,
italic, inline code, fenced code blocks, bullet and numbered lists, block quotes, a rule, paragraph
breaks) and renders each block in the app's own type scale and colours: code in the mono face on the
secondary surface with horizontal scroll, lists with hanging indents, quotes with a left bar. It is
deliberately tolerant of half-finished Markdown so a response renders correctly as it streams (an
unclosed ** or ``` shows as plain text rather than breaking). No web-view, no third-party library, so
it stays offline and on-brand. Selection still works (the renderer's Text nodes sit inside the same
SelectionContainer), and copy keeps the raw Markdown which pastes sensibly.

Guidance: added a "How you shape an answer" section to the shared HARD_RULES (so it applies in every
mode). It tells the model to match structure to content and, just as importantly, not to over-format:
a short question gets one or two sentences with no heading or list; steps get a numbered list;
parallel options get bullets; only a long multi-subject answer gets short headings; code goes in a
fenced block; comparisons stay plain text rather than tables (which read badly on a phone); and it
must not add headings to short answers, bullet prose, restate the question, or append a summary.

Also suppressed the empty answer bubble during streaming: the thinking indicator stands in for an
answer that has not produced text yet, so the bare pill no longer flashes.

Verified on device (E2B): "capital of France" -> one plain sentence; "3 steps to brew tea" -> a
numbered list; "Python hello world in a code block" -> just the code block, no preamble. Tests:
MarkdownParseTest (parser, incl. mid-stream tolerance) and FormattingGuidanceTest (guidance present
in every mode incl. grounded Discover).

### Item 17: conversation titles (root cause: titling was wired to one screen)

Titling lived inside ChatViewModel.respond(), so only in-app chat turns triggered it. A conversation
created through any other entry point never got a title: the power button overlay's handoff created
the conversation and saved the Q and A directly (the Eiffel Tower "no title" bug the owner reported),
and an interrupted generation left a titleless conversation for good. Title quality was also weak
(the instruction produced literal "Title"), and a null title showed the generic "New conversation".

Fixed by making titling a shared property of a conversation gaining content. New `ConversationTitler.
titleIfNeeded(repository, engine, conversationId)` is the single path, called from: respond()'s finally
(in-app and Discover, since those flow through it), the overlay handoff (so an overlay conversation
arrives already named), and ChatViewModel.open() as a safety net that titles any opened conversation
that has content but no title (interrupted generations, older entry points). The share/selection and
widget/tile paths open a new in-app chat whose first send flows through respond(), so they are covered.

Quality: the instruction now asks for a short specific title naming the actual subject and forbids the
words "title" and "conversation"; the result is cleaned (quotes, markdown, stray punctuation stripped)
and, if blank or generic, replaced by an honest excerpt of the first user message rather than a
placeholder. Manual renames still win (titleIsManual), and an auto title refreshes once at 8 messages.

Efficiency (owner's "when it runs" instruction): titling never loads a multi-gigabyte model on its own.
When the model is already resident it writes a model-quality title; when it is not (e.g. titling on
open right after launch) it uses the instant excerpt fallback, and a model title can still replace it
at the refresh milestone. title-on-open is cancelled the instant a real reply starts, so a title pass
and a reply never share the single-threaded engine. Verified on device: an interrupted "tell me about
paris" conversation, previously blank, is titled "tell me about paris" on open; fresh chats get model
titles like "Eiffel Tower height measurement". Tests: ConversationTitlerTest.

### Item 12: Logic Partner (visual distinction, inline switch notice, verified behaviour)

The mode switch already changed the system prompt for the next turn (buildPrompt uses
SystemPrompts.forMode of the current mode), but it was invisible and not persisted. Added:

- A new Role.SYSTEM for display-only transcript markers (Role is stored by name, so this needs no
  migration). SYSTEM entries are filtered out before the prompt is built (never sent as a turn), out
  of the titler's content check, and out of the chat-list snippet.
- setMode now persists the conversation's mode (survives reopening) and drops a quiet centered SYSTEM
  note into the transcript at the switch point, but only once there is real content to mark. The copy
  is the owner's: entering Logic explains it will argue the other side and concede when you are right;
  returning to Chat says it will answer normally.
- Visual distinction while Logic is active: the mode pill reads "Logic Partner" in the tonal fill, and
  a calm persistent banner sits under it ("Logic Partner is testing your reasoning, not agreeing with
  it"). Design system only, never the reserved amber.

Verified on device with the same model and the same kind of claim: in Chat, "I want to quit my job to
day trade full time" got a helpful, go-along answer; after switching to Logic, "Day trading is
basically guaranteed money" got "That is an assumption. Day trading is not guaranteed money ... you
are setting yourself up for significant financial loss." Both switch notes appear in the transcript
and the full history carries across the switch. Test: ModeSwitchTest.

### Items 4 and 19: chat-row swipe rail geometry

Two defects in the same rail. The buttons were a fixed 52dp square (item 4: they stood taller or
shorter than the row, which varies by view), and RAIL_WIDTH (175dp) was narrower than the four
buttons needed (~228dp), so the leftmost action (Rename) stayed hidden under the row when open
(item 19). Fixed by drawing the rail behind the row with matchParentSize (so it is exactly the
row's height in any view) and making each button fillMaxHeight with the row's corner radius; and by
widening RAIL_WIDTH to 232dp with the four buttons each taking an equal weighted share, so all four
are revealed and reachable at the open position. Verified on device in the compact and cozy views.

### Item 20: open-chat header and archived view

The open conversation now shows its title at the top (ChatViewModel exposes a reactive `title` from
observeConversation, so it updates the moment a title is set). The title sits in a small header with
a short accent bar marking it as the title and a hairline separating the header zone (title + mode
switcher) from the messages, added after the owner noted the plain title was hard to read as a
title. An overflow menu holds Rename, Archive, and Delete, using the same view-model actions and
confirmation tiers as the chat list; a manual rename here sets titleIsManual and stops auto-titling.
Archive and delete from the header pop back to the list via an onExit callback threaded from the nav
stack, and delete pops only after the confirmation is accepted (deleteConversation/archive gained an
onDone callback).

Archived conversations get their own screen (Pushed.Archived), reached from a quiet "Archived (N)"
link that appears on the Chats list only when some exist, so it never clutters the main list. Each
archived chat can be opened, moved back to Chats (unarchive, reversible), or deleted (not). Verified
on device end to end: header title and menu, archive -> the chat leaves the list and the link
appears -> the archived view lists it -> Move to Chats restores it.

### Item 15: system-wide custom instructions + the instruction precedence order

Added a Settings > Custom instructions screen: one field, capped at 2000 characters (~500 tokens,
a sensible slice of a small window) with the remaining room shown so nothing is silently truncated.
Stored in the settings key-value table and re-injected on every turn (small models drift), via
SystemPrompts.withUserInstructions.

Precedence, documented here and enforced by the composition order in ChatViewModel.buildPrompt and
guarded by InstructionPrecedenceTest:

  1. The app's fixed mode instructions and hard rules (identity, safety, no-characters, no-roleplay,
     no-sycophancy). Stated first, declared non-overridable. These always win.
  2. The user's system-wide instructions (this feature).
  3. The project's instructions, when the conversation belongs to a project.
  4. Memory.

Each user-provided layer (user instructions, project instructions) is told in the prompt to follow
its content "unless it conflicts with anything above", so nothing below can override the app's rules
or, in a project, the user's own standing instructions. Device-verified end to end: a custom
instruction to end every answer with a marker word was obeyed in a fresh chat.

### Item 16: memory system made real

What existed: manual ("remember that ...") and an Auto one-shot after every exchange; storage with
exact-text dedup; retrieval was mostRecent(N) with NO relevance; injection via withMemory. Honest
gap: retrieval was recency-only, which the owner flagged as the biggest quality risk on a small model.

Done:
- Retrieval by relevance (MemoryRetrieval.select): each memory scored by keyword overlap with the
  current message (prefix-matched so "peanut" hits "peanuts") plus a small recency bonus, filling a
  budget of ~10% of the context window at most MEMORY_LIMIT entries. Injected near the front of the
  system block where models attend well. A clean seam remains for semantic retrieval when on-device
  embeddings land. Pure and unit-tested.
- Extraction as a cheaper batch: Auto runs over the last few turns only every AUTO_MEMORY_EVERY user
  messages, not after every one, and is given the already-stored facts so it does not re-suggest them.
- Dedup on a normalised form (case/punctuation/spacing) instead of exact text; the auto-reply parser
  strips chat-template tokens ("NONE</start_of_turn>", "<end_of_turn>") that had been stored as junk.
- Transparency/control already present in the Memory screen (see all in full, auto vs manual, edit,
  delete, multi-select, delete all, mode switch).

Verified on device: told "remember that I am allergic to shellfish" in one chat; a separate later chat
answered "Shellfish" to "name one food I must avoid", proving extraction, retrieval, and cross-chat
injection. Remaining refinements (issue #16 stays open): full contradiction supersession (today the
recency component simply ranks a newer conflicting fact above the older one) and an optional indicator
that a given response was influenced by memory.

## Item 9 — Unified saving (one bookmark, one destination)

Owner decision: there should be one saving action and one destination across the whole app. The
bookmark icon means the same thing everywhere, and everything saved lands in the single Follow-ups
list, distinguished by the source filter (item 10). Remove the separate Discover "Saved moments"
feature rather than keeping two lists doing nearly the same job. Keep the Discover page's own saved
section, but as a filtered view of the one list, reading the same data rather than a parallel store.
Migrate any existing saved moments so nothing is lost. This also keeps the future Today design (which
assumes a single saved destination with source filtering) consistent instead of adding a third pattern.

How it was built:
- FollowUpEntity gains packId/momentId. A saved Discover moment is an ordinary follow-up whose source
  is DISCOVER and whose snippet is the moment title; the two ids let it reopen as a grounded discussion.
- saveMoment/unsaveMoment/isMomentSaved/observeSavedMoments now read and write follow_ups (via
  countMoment/deleteMoment/observeSavedMoments on FollowUpDao). The whole discover_saved table, its
  SavedMomentEntity, and the DiscoverDao save/unsave/observeSaved/isSaved methods are deleted.
- DB version 3 -> 4, MIGRATION_3_4: add packId/momentId columns to follow_ups, INSERT ... SELECT the
  existing discover_saved rows into follow_ups (title -> snippet, savedAt -> createdAt), then DROP the
  old table. A real migration, never a destructive fallback.
- BackupCodec: follow-up encode/decode carry packId/momentId; the Snapshot's separate `saved` list is
  removed. On import, a legacy backup's "saved" array is folded into follow_ups (legacySavedAsFollowUp)
  so importing an older file loses nothing. FORMAT_VERSION bumped 1 -> 2.
- Repository gains openMomentDiscussion(packId, momentId), shared by the Discover view model and a new
  AppViewModel.openSavedMoment, so the Follow-ups list can reopen a saved moment without pulling in the
  Discover view model. FollowUpsScreen routes a tap on a moment-bearing follow-up to onOpenMoment.

Verified on device: dealt a Discover moment, bookmarked it, saw it appear in the single Follow-ups list
under the DISCOVER source chip and in Discover's own Saved section (same data); reopened the grounded
discussion from both the Follow-ups list and the Discover Saved section; toggled the bookmark off and
it left the one list. The 3->4 migration ran cleanly over the phone's existing data with no crash.

## Item 21 — Discover scope boundary visible, with a one-tap way out

A grounded Discover discussion confines the model to a saved passage. That boundary was invisible: a
person could ask something the passage does not cover and get a flat "the passage does not say",
a dead end. Now the scope is stated up front and there is a one-tap escape.

- ChatViewModel exposes a `grounded` flow (conversation.groundingMomentId present) and
  `continueInOpenChat()`, which clears the grounding, switches the conversation to open Chat, and adds
  a quiet SYSTEM note. The mode switch matters: with the passage gone, a conversation left in Discover
  mode would resolve to DISCOVER_GROUNDED pointing at nothing, so lifting scope must also open the mode.
- ChatScreen shows a GroundedBanner when grounded, mirroring the Logic banner: tonal fill, book icon,
  no amber, with "Continue in open chat" as a plain accent action. Repository.clearGrounding +
  ConversationDao.clearGrounding back it; SystemPrompts.CONTINUE_OPEN_NOTICE is the boundary note.

Verified on device: opened a grounded discussion (banner shown, scope stated), tapped Continue in open
chat (banner gone, honest note added, mode = Chat), reopened the conversation and the change persisted
(no banner, still Chat) - so grounding was cleared in the database, not just in memory.

This advances item 21 (scope stated up front + out-of-scope escape carrying context). The scoped
slide-up surface (item 11) and a broader audit for other invisible walls remain.

## Documentation reconciliation and two standing process rules (2026-07-23)

MASTER_SPEC.md and DESIGN.md had drifted from the built app: they still described the follow-up
flag (now a bookmark), a bottom-nav "New" item (removed; new chat lives on the Chats screen, nav is
Projects/Chats/Follow-ups/Discover with Today planned first), generic "research a model per tier"
(now Gemma 4 across every tier with declared per-model capabilities), a plaintext database (now
SQLCipher-encrypted with an optional app lock), a separate Discover saved-cards store (now unified
into the single Follow-ups list with source filtering), and a plain grounded chat (now with a scope
banner and a one-tap Continue in open chat). Both documents were rewritten to describe the app as it
now is and as it is intended to be, correcting every superseded instruction in place and marking
pending work as pending, with the open GitHub issues left as the record of what remains. A precedence
statement was added at the top of MASTER_SPEC.md.

Two process rules now apply permanently:

1. Living documents. Every commit updates MASTER_SPEC.md, DESIGN.md, and any other spec or design
   document so they always describe the app as it currently is and as it is intended to be.
   Superseded instructions are corrected, not left beside their replacements; anything still pending
   is marked pending, not described as built. This is part of the definition of done for every
   change, not periodic cleanup.

2. GitHub Issues used fully, the way a working developer would. Open an issue for every bug, feature,
   or enhancement, including ones found rather than reported. Label and categorize them. Keep real
   working notes on each issue as progress is made rather than only closing them at the end.
   Reference issue numbers in commit messages so commits and issues link together. Close an issue
   only when the work is genuinely finished and verified on the device. Open issues are the
   authoritative record of what remains, so anyone picking up the project, including an outside
   contributor, can see its real state.

Precedence for future sessions: MASTER_SPEC.md, DESIGN.md, DECISIONS.md, and the open GitHub issues
are the current source of truth. Anything in older prompts or earlier conversation that conflicts
with them is superseded. The built app, git history, DECISIONS.md, and the issues win over any
document wherever they disagree, and the documents are then corrected to match.

### A discrepancy found during reconciliation, logged as an issue

The bookmark on a chat response fills amber when set (the reserved amber rule for saved items), but
the save bookmark on a Discover card fills with the accent colour instead. With saving unified, both
are the same save-to-Follow-ups action and should look the same. This is a real inconsistency, left
in the code and tracked as a GitHub issue rather than fixed under this documentation-only task.

## Item 18 — Power button assistant polish (quiet visual character, both themes)

The earlier pass fixed the functional gaps (input locks while generating with a Stop control, the mic
made reactive from the active-speech-model flow, and a Settings toggle for the default input mode).
What remained was quiet visual character on the overlay sheet and confirming it via the real gesture.

Added, all from theme tokens so a single implementation works in both light and dark (the owner's
requirement): a small grabber handle matching the app's other bottom sheets; the "on device" mono tag
next to the name that DESIGN.md section 7 always called for and the overlay was missing; the mark
breathing while an answer streams, its status-indicator behaviour from section 2; a faint black scrim
(0.32 alpha, theme-neutral, dims whatever is behind in either theme) so the panel reads as lifted; and
a slide-up arrival on the expressive spring, the "sheet arriving" signature moment from section 6, that
collapses to instant under reduced motion.

Verified on device via the real assist gesture (adb KEYCODE_ASSIST, which the OS routes through the
registered assistant service to OverlayActivity, the same path as long-press power, not the blocked
non-exported am-start path). The overlay renders correctly in both light and dark: header, tag,
handle, and accent buttons all adapt; the mic is present in the empty state and hides once the field
has text; asking locks the input and turns send into Stop while thinking; the keyboard pushes the panel
up cleanly. This closes issue #18.

## The Four-Mode Update (2026-07-24)

A large update: a new Brainstorm mode, four sibling modes with new identity, and a set of usability
gaps. Tracked as issues #24 through #39. This section records the decisions; each piece is landing as
its own commit and issue.

### Foundation: data model, Chat -> General, Brainstorm prompt (issues #24, #25, #28 copy)

Mode enum is now GENERAL, LOGIC, BRAINSTORM, BENCH, DISCOVER, OVERLAY. Chat was renamed to General
because, with four modes, calling one of them Chat implied the others were not conversations; the four
are parallel siblings. The Chats bottom-nav tab keeps its name since it holds every conversation.

A conversation now records every mode it has used, as a comma-separated ordered list (modesUsed) on the
conversation row, seeded from the current mode and appended on each switch, never duplicating. This is
what the chat-row mode dots and the mode filter read. Kept denormalized on the row rather than in a
join table because the list is tiny and always read with the row.

Follow-ups gained a kind (CHECK or PURSUE, Part 5), defaulting to check, set from the source at save
time and overridable later.

DB migration 4 -> 5 (MIGRATION_4_5): rewrites mode 'CHAT' -> 'GENERAL' in conversations and follow_ups,
adds modesUsed seeded from each conversation's mode, and adds the follow-up kind column defaulting to
check. A real migration, verified on the device's populated database: launched clean, every
conversation intact, no data loss. The Room Converters and the backup codec both map a stray 'CHAT'
string to GENERAL so older stored rows and older backup files import rather than throwing.

Brainstorm's system prompt (Part 1) is written as short ordered rules, not a decision tree, because a
small on-device model follows a checklist far more reliably. It encodes the defining rule (pull ideas
out of the user, never hand them over), the never-rules (never open with a list, never be impressed,
never answer its own question, always converge), one-question-at-a-time, the twelve-rule method
selection checklist covering all ten methods plus wishing, the two-method cap, and the convergence step
with the Logic handoff and save-to-Follow-ups. It runs at conversational sampling for range. This is
the honest fit for a small model, which is weak at generating and strong at working with supplied
material, which is the thesis of the whole app.

Four-mode copy is in SystemPrompts: topBanner(mode) for the one-line banners and modeSwitchNotice(mode)
for the midstream notices, with Workbench's notice worded as a linked session per Part 4.

Remaining four-mode work is tracked in the open issues (#26 colors, #27 segmented control, #28 picker/
notices UI, #29 nudges, #30 dots/filter, #31 auto-archive, #32 Workbench linking, #33 follow-up kinds
UI, #34 keyboard, #35 nav/failure, #36 onboarding/copy, #37 Today cancellation, #38 performance, #39
usability gaps).
