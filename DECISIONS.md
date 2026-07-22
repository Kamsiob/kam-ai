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

## BLOCKED

Items that genuinely cannot be completed without the owner. Each says exactly
what he needs to do.

Nothing at present.

The Pixel initially reported as `unauthorized` over ADB, which would have blocked
every on-device step. It authorised itself once the ADB daemon restarted, and the
phone (Pixel 10 Pro XL, Android 17, 16 GB) has been running builds and tests
since. No action needed.
