# Every permission, the feature that needs it, and where it is explained

Written for the store submission, where an unjustified permission is a common
rejection cause, and re-derived from the built application rather than from what
was declared before.

The list is exhaustive and is asserted by `PrivacyClaimsTest`, which fails if the
package requests anything not on it. That test found `USE_BIOMETRIC` when this
list was first written by reading the manifest, because it arrives from a
dependency's merged manifest and appears nowhere in Kam AI's own.

| permission | the feature | where the user is told |
|---|---|---|
| `INTERNET` | Downloading a model, a voice or a content pack, and fetching the content pack list when Discover is opened | Full description, "the internet, only to download a model, a voice or a content pack when you choose to, and to check what content packs are available when you open Discover" |
| `ACCESS_NETWORK_STATE` | Whether there is a connection and whether it is metered, so a multi-gigabyte model never starts on cellular unasked (#79) | Full description, "whether you have a connection and whether it is metered" |
| `FOREGROUND_SERVICE` | A model download runs for twenty minutes and must survive the app being backgrounded | Full description, "a notification while a download is running, so it can carry on when you leave the app" |
| `FOREGROUND_SERVICE_DATA_SYNC` | The type that service declares, required by the platform for the above | Same sentence |
| `POST_NOTIFICATIONS` | The download progress notification, and nothing else | Same sentence |
| `RECORD_AUDIO` | Voice typing, transcribed on the phone with whisper.cpp. Requested at first use, and the microphone is not required to install | Full description, "the microphone, only when you use voice typing, which is transcribed on the phone" |
| `USE_BIOMETRIC` | The app lock | Full description, "your fingerprint or face, only if you turn on the app lock" |

## What is deliberately absent

`USE_FINGERPRINT` is explicitly removed in the manifest with `tools:node="remove"`.
The minimum SDK is 31, so it could never be used, and leaving it would put a
permission on the listing that does nothing except need explaining.

`RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` were pulled in by WorkManager while it was
unused and are asserted absent. An application that only touches the network when
asked has no business starting itself at boot.

There is no advertising ID permission, and no analytics, crash reporting or
telemetry library in the build.

## The one that needs care in the data safety form

`FOREGROUND_SERVICE_DATA_SYNC` names a sync, and this application syncs nothing.
The declared type is what the platform requires for a long-running download, and
the data safety answers must not be read across from the permission name: no data
is collected, none is shared, and nothing leaves the device.
