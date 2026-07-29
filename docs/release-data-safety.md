# Data safety, re-derived from the built application

Google decompiles the build and cross-checks the declaration against what the code
actually does. Inaccuracy here is one of the top rejection causes, and the previous
declaration predates the foreground service, the background download work and the
voice path. So this is derived from the source as it stands rather than carried
forward.

## Every network call in the application

There are two, and both are in one HTTP client.

1. **Downloads**, in `Downloader`. Models, voices and content packs, each started
   by the user tapping a button that names the size. Nothing downloads on its own.
2. **The content pack list**, `KamRepository.fetchDiscoverManifest`, fetched when
   the Discover screen is opened. It is a request for a static manifest on a
   GitHub release. It sends nothing about the user, and it fails silently to an
   empty list when offline.

That is the complete list. It was derived by finding every call site of the HTTP
client rather than by reading feature descriptions.

## What does not exist, and must not be declared

- **No telemetry, analytics or crash reporting.** No such library is in the build,
  and the absence is asserted rather than assumed.
- **No update check.** The application never asks whether a newer version exists.
- **No prefetching.** Nothing is fetched in anticipation of being wanted.
- **No background connections.** The one foreground service exists to keep a
  user-started download alive and does nothing else.
- **No advertising ID**, and the permission is asserted absent.
- **No web search.** The setting keys exist and the row is gated off, so the
  feature is not in the built application. The store listing said otherwise and has
  been corrected, as had the in-app questions and answers screen.

## The declaration

| question | answer | why |
|---|---|---|
| Does the app collect or share any user data? | **No** | Nothing the user types, says or saves leaves the device. The two network calls send no user data. |
| Is data encrypted in transit? | Not applicable, no data is collected | The downloads are HTTPS, but nothing about the user is sent. |
| Can users request deletion? | Not applicable, no data is collected | Everything is on the device and deletable in the app: chats, memories, follow-ups, packs, and the model itself. |
| Location, personal info, financial info, health, messages, photos, files, contacts, calendar, app activity, web browsing, device identifiers | **None collected** | Derived per category from the network audit above rather than answered as a group. |

**Audio requires care.** The microphone is used for voice typing and the audio is
transcribed on the device by whisper.cpp. It is never uploaded and never stored as
audio. The correct answer is that audio is not collected, and the reasoning is
worth writing on the form if there is room, because a microphone permission with
"no data collected" is the pairing a reviewer looks at twice.

**`FOREGROUND_SERVICE_DATA_SYNC` requires the same care.** The permission names a
sync and this application syncs nothing. It is the type the platform requires for a
long-running download. The data safety answers must not be read across from the
permission's name.

## What to re-check if the build changes

Any new call to `downloader.httpClient`, any new dependency that brings its own
networking, and any feature that is currently gated off being turned on. The first
two are found by searching for `newCall(`; the third is found by searching for the
`available` flags in the settings screen.
