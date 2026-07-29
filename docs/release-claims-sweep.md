# The network and data claims sweep

Three false statements about network behavior were found in three days: an upload
queue that does not exist, a web search that is not in the build, and a privacy
policy promising no network requests. That is a pattern, not three incidents, and it
is the one category where being wrong is unrecoverable, because a screenshot of the
application making a false privacy claim outlives the correction.

This is the checklist. Run it before any release, and after any change that touches
networking or removes a feature.

## Ground truth: what the application actually does

Derived by finding every call site of the HTTP client, not by reading feature
descriptions. **There are exactly two network calls.**

1. **Downloads.** Models, voices and content packs, each started by the user
   tapping a button that names the size. `Downloader`.
2. **The content pack list.** Fetched when the Discover screen is opened.
   `KamRepository.fetchDiscoverManifest`. Sends nothing about the user, fails
   silently to an empty list offline.

**Not present:** telemetry, analytics, crash reporting, update checks, prefetching,
background connections, advertising ID, web search.

To re-derive: `grep -rn "newCall(" app/src/main/java`. Two files should appear. If a
third does, the ground truth has changed and every statement below must be
re-checked.

## Every place a claim can live

Check each against the two calls above. Where two sources disagree, one is wrong,
and which one is the interesting question.

- [ ] `tools/play/listing.json`, full description
- [ ] `docs/release-data-safety.md`, the declaration itself
- [ ] `PRIVACY.md`
- [ ] The hosted privacy copy, if it diverges from `PRIVACY.md`
- [ ] `README.md`
- [ ] `docs/index.md`, the website
- [ ] `OnboardingCopy.kt`, every slide
- [ ] `QuestionsAndAnswers.kt`, every entry
- [ ] Every settings row title and subtitle, `SettingsScreens.kt`
- [ ] Every empty state, including Discover's and Chats'
- [ ] The model picker and the download screens
- [ ] The storage screen
- [ ] **The model's own answers**, which is the one that is not a string in the
      codebase and therefore the easiest to skip

## The model's answers

Not greppable, and the source of the worst of the three. Ask directly, in a fresh
conversation, in **every mode** and on **both tiers**:

- Does this use the internet?
- Where does my data go?
- Is anything I type uploaded?
- Does it work offline?
- Is there a server?
- What happens to what I type when I have no signal?

Every wrong answer is the same defect as the two found in prose and blocks release.
`tools/session.sh` held for several turns as somebody who does not trust the
application is what found the upload-queue answer.

Two worked examples now cover the two questions that failed, and both are exempt
from every guard check because both are true wherever they land. A third failing
question wants the same treatment: an example, in the shared hard rules, exempt.

## What this sweep has caught

| where | claim | reality |
|---|---|---|
| the model's answer | text "stays on this phone until you have a connection" | no queue exists (#136) |
| the model's answer | "are you using the native application or a web browser version?" | no web version (#135) |
| store listing | the internet is used "for web search if you switch that on" | not in the build |
| in-app questions | "or if you set up web search yourself" | not in the build |
| `PRIVACY.md` | web search, as a bullet | not in the build |
| `PRIVACY.md` | "makes no network requests" if you never download | Discover fetches the pack list |
| `README.md` | "no transport, no server and no network code" | true of sync only |

All three prose failures were written before or alongside a feature and drifted
silently. None were in code. **Documentation does not fail loudly, so it has to be
checked against the build rather than against the last version of itself.**
