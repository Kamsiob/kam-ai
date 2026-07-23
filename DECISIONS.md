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
