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
