# Play Console: the parts with no API

Everything here has to be typed into the Console by hand. It was confirmed by
introspecting the Android Publisher API rather than by memory: `AppDetails`
exposes only `contactEmail`, `contactPhone`, `contactWebsite` and
`defaultLanguage`; `Listing` exposes only `title`, `shortDescription`,
`fullDescription` and `video`; and there is no data safety, privacy policy or
category resource anywhere in the edits API.

Everything the API **can** reach is written and ready to go, in
`tools/play/listing.json` and `store-assets/`: the title, the short and full
descriptions, the icon, the feature graphic, and the phone screenshots.

**None of it has been uploaded.** An earlier version of this line said it was
already uploaded and verified, and that was wrong. Nothing has ever been sent to
the Console. That is worth stating plainly rather than quietly correcting,
because a reader who believed it would skip the upload and then wonder why the
listing was empty. The one consolation is the one HANDOFF already records: with
nothing uploaded, no superseded asset can be lingering up there.

Answers below are derived from the release build's actual behaviour, not from
what was declared before. The foreground service work changed the permission set
since then.

Do them in this order.

---

## 1. App category and tags

**Category:** Productivity

Not Tools, which is where utilities and system helpers sit, and not Education.
The app's own description leads on thinking and drafting, and Productivity is
where a person looking for that would browse.

**Tags:** choose from the Console's fixed list. The closest are *Notes*,
*Writing*, and *Personal organiser* if offered. Do not take a tag that implies a
chatbot companion or an assistant persona, because the app explicitly is not one
and the store listing says so.

---

## 2. Privacy policy URL

```
https://kamsiob.com/kam-ai/privacy
```

Confirm that URL is live before saving. If it is not yet published, the fallback
that is definitely reachable is the copy in the repository:

```
https://github.com/Kamsiob/kam-ai/blob/main/PRIVACY.md
```

A store listing pointing at a privacy policy that 404s is a policy violation on
its own, so this is worth loading in a browser rather than assuming.

---

## 3. Data safety

The whole form. The short version is that **nothing is collected and nothing is
shared**, and every answer below follows from that.

### Data collection and sharing

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |

Answering No closes the entire data types section. That answer is accurate, and
the rest of this section explains why, so it can be defended if it is ever
queried.

**Nothing leaves the device.** Conversations, memory, projects, follow-ups and
settings are written to a local SQLCipher encrypted database. The model runs on
the phone. There is no account, no server belonging to this app, and no analytics
or crash reporting library in the build. Checked in the dependency list:
no Firebase, no Crashlytics, no Sentry, no analytics SDK of any kind.

**The app does make network requests, and none of them carry user data.** The
complete set of hosts the code can contact:

- `huggingface.co`, to download a model file the user chose
- `github.com`, to download Discover content packs and to check for releases
- `kamsiob.com`, `youtube.com`, `t.me`, `buymeacoffee.com`, only when the user
  taps a link that opens the browser

Each is a download or an outbound link. None is a report, and none carries
anything the user typed.

### Security practices

| Question | Answer |
|---|---|
| Is all user data encrypted in transit? | **Yes** |
| Do you provide a way for users to request data deletion? | **Yes** |

Encrypted in transit is Yes because every network call is HTTPS through OkHttp
with cleartext disabled.

Data deletion is Yes, and the honest form of the answer is that data never leaves
the device, so deletion is local and immediate: Settings, then Delete everything.
There is no server-side copy to request the deletion of.

### Data types

Leave every category unticked. Specifically, and these are the ones a reviewer
would expect to see ticked for an app of this kind:

- **Personal info:** not collected. There is no account and no sign-in.
- **Messages:** not collected. Conversations stay in the local encrypted database.
- **Audio:** not collected. Voice is transcribed on the device by whisper.cpp and
  the audio is not retained or transmitted.
- **Files and docs:** not collected. An attached document is read locally and
  never uploaded.
- **App activity and diagnostics:** not collected. No analytics, no crash
  reporting.

### Microphone

The `RECORD_AUDIO` permission is declared and will be visible. If asked to
explain it: the microphone is used for on-device speech to text, and the audio
does not leave the phone.

---

## 4. Content rating questionnaire

Answer it honestly rather than optimistically. The answers that matter:

- No violence, no sexual content, no profanity from the app itself.
- **Does the app allow users to interact with each other?** No. There is no
  social feature, no sharing between users, no accounts.
- **Does the app allow users to purchase items?** No.
- **Does the app share the user's location?** No.
- **User generated content:** the user types into it and an on-device model
  replies. Nothing is published, shared, or visible to anyone else. If the form
  insists on a UGC category, the accurate description is that content is created
  and stored locally and never transmitted.

---

## 5. Ads declaration

**Does your app contain ads? No.**

There are none, and the listing says so in its first line.

---

## 6. Target audience

Not directed at children. Choose the adult age bands. The app is a writing and
thinking tool, and declaring a child audience would bring Families policy
requirements that do not fit it.

---

## 7. App access

If the Console asks whether any part of the app is restricted behind a login:
**no login is required, all functionality is available without an account.**

Worth adding a note for the reviewer, because the app is unusable until a model
is downloaded and a reviewer may otherwise report it as broken:

> On first launch the app downloads a language model, about 5 GB, chosen by the
> device's memory. Everything runs on the device after that. On a slow connection
> the download takes a while, and the app is deliberately not usable for
> conversation until it finishes.

---

## What is already done, for reference

Uploaded through the API and verified by reading it back:

- Title: `Kam AI: Private Offline AI` (26 of 30 characters)
- Short description: 77 of 80 characters
- Full description: about 1,780 of 4,000 characters, including the limitations
  section
- Icon, 512 by 512
- Feature graphic, 1024 by 500
- Five phone screenshots

Contact email and website were already correct and were not changed.
